package com.ticketbooking.stock.mq;

import com.ticketbooking.common.constant.RedisExpireConstants;
import com.ticketbooking.common.constant.RedisKeyConstants;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.OrderStatus;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.model.dto.OrderDTO;
import com.ticketbooking.common.model.qo.CreateOrderQO;
import com.ticketbooking.common.mq.TicketOrderMessage;
import com.ticketbooking.common.utils.RedisUtils;
import com.ticketbooking.stock.client.OrderServiceClient;
import com.ticketbooking.stock.config.KafkaTopicConfig;
import com.ticketbooking.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 订单消息消费者
 *
 * 设计原则：
 * 1. Redis 是预扣减层，DB 是真实库存层
 * 2. 消费失败不回滚 Redis（库存不足场景），只标记订单失败状态
 * 3. 限购校验失败需要回滚 Redis（业务校验失败场景）
 * 4. Redis 库存由定时任务与 DB 同步保证最终一致性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {

    private final OrderServiceClient orderServiceClient;
    private final StockService stockService;
    private final RedisUtils redisUtils;

    @KafkaListener(topics = KafkaTopicConfig.TICKET_ORDER_TOPIC, groupId = "stock-group", concurrency = "5")
    public void processOrder(@Payload TicketOrderMessage message, @Header(KafkaHeaders.RECEIVED_KEY) String key, Acknowledgment acknowledgment) {
        String orderNo = message.getOrderNo();
        log.info("Processing order message: {}", orderNo);

        try {
            // 1. 幂等检查
            if (!acquireConsumeLock(orderNo)) {
                log.info("Order already processed (idempotent): {}", orderNo);
                acknowledgment.acknowledge();
                return;
            }

            // 2. 检查订单是否已存在
            OrderDTO existingOrder = orderServiceClient.findByOrderNo(orderNo);
            if (existingOrder != null) {
                log.info("Order already exists in DB: {}", orderNo);
                acknowledgment.acknowledge();
                return;
            }

            // 3. 最终防线：检查用户是否超出限购
            int purchaseLimit = getPurchaseLimit(message.getConcertId());
            int purchasedCount = getUserPurchasedCount(message.getUserId(), message.getConcertId());

            if (purchasedCount + message.getQuantity() > purchaseLimit) {
                log.warn("Purchase limit exceeded: userId={}, concertId={}, purchased={}, limit={}, request={}",
                         message.getUserId(), message.getConcertId(), purchasedCount, purchaseLimit, message.getQuantity());

                // 超出限购，回滚 Redis 并创建失败订单
                rollbackRedis(message);
                // 回填 userPurchaseKey 为 DB 真实值，让后续 Lua 请求能直接拦截
                backfillUserPurchaseKey(message.getUserId(), message.getConcertId(), purchasedCount);
                createFailedOrder(message, "超出限购数量，您已购买 " + purchasedCount + " 张，限购 " + purchaseLimit + " 张");
                acknowledgment.acknowledge();
                return;
            }

            // 4. 先创建处理中的订单
            createProcessingOrder(message);

            // 5. DB 乐观锁扣减库存
            int updated = stockService.decrementStock(
                    message.getConcertId(),
                    message.getGradeId(),
                    message.getQuantity(),
                    orderNo);

            if (updated == 0) {
                // DB 扣减失败 = 真的没库存了，标记订单失败
                // 注意：这里不回滚 Redis，因为 Redis 只是预扣减
                log.warn("Stock decrement failed (sold out): orderNo={}", orderNo);
                markOrderFailed(orderNo, ErrorCode.TICKET_SOLD_OUT.getMessage());
            } else {
                // 扣减成功，订单进入待支付状态，等待用户支付后由 payment 模块回调置为 PAID
                log.info("Stock decremented successfully: orderNo={}", orderNo);
                updateOrderToPending(message);
                // 回填 userPurchaseKey 为 DB 真实总数（含本次），修正 Lua 层可能的不准确计数
                backfillUserPurchaseKey(message.getUserId(), message.getConcertId(), purchasedCount + message.getQuantity());
            }

            acknowledgment.acknowledge();
            log.info("Order processed successfully: {}", orderNo);

        } catch (BusinessException e) {
            // 业务异常：标记订单失败，不再重试
            log.error("Business error processing order: orderNo={}, error={}", orderNo, e.getMessage());
            markOrderFailed(orderNo, e.getMessage());
            // 删除幂等 key，允许重新下单
            deleteConsumeLock(orderNo);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            // 系统异常：删除幂等 key，稍后重试
            log.error("System error processing order: orderNo={}", orderNo, e);
            deleteConsumeLock(orderNo);
            acknowledgment.nack(Duration.ofSeconds(1));
        }
    }

    /**
     * 获取演唱会限购数量
     * 优先从 Redis 获取，否则返回默认值
     */
    private int getPurchaseLimit(Long concertId) {
        try {
            String limitKey = RedisKeyConstants.buildConcertLimitKey(concertId);
            String limit = redisUtils.get(limitKey);
            if (limit != null) {
                return Integer.parseInt(limit);
            }
        } catch (Exception e) {
            log.warn("Failed to get purchase limit from Redis: concertId={}", concertId, e);
        }
        // 默认限购 1 张
        return 1;
    }

    /**
     * 获取用户已购买数量（从 DB 查询）
     */
    private int getUserPurchasedCount(Long userId, Long concertId) {
        try {
            Integer count = orderServiceClient.countUserPurchased(userId, concertId);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Failed to get user purchased count: userId={}, concertId={}", userId, concertId, e);
            // 查询失败返回 0，允许继续下单（降级策略）
            return 0;
        }
    }

    /**
     * 回滚 Redis（限购校验失败时调用）
     * 1. 恢复 Hash 库存
     * 2. 减少用户购买计数
     *
     * 注意：如果 key 已过期不存在，跳过回滚，避免 HINCRBY 自动创建错误的值
     */
    private void rollbackRedis(TicketOrderMessage message) {
        try {
            // 使用 Hash 结构的库存 Key
            String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(message.getConcertId());
            String userPurchaseKey = RedisKeyConstants.buildUserConcertPurchaseKey(message.getConcertId(), message.getUserId());

            // 恢复 Hash 库存（仅当 Hash field 存在时）
            if (Boolean.TRUE.equals(redisUtils.hExists(stockHashKey, String.valueOf(message.getGradeId())))) {
                redisUtils.hIncrBy(stockHashKey, String.valueOf(message.getGradeId()), message.getQuantity());
            } else {
                log.warn("stockHash field not exists, skip rollback: concertId={}, gradeId={}",
                         message.getConcertId(), message.getGradeId());
            }

            // 减少用户购买计数（仅当 key 存在时）
            if (Boolean.TRUE.equals(redisUtils.hasKey(userPurchaseKey))) {
                redisUtils.decrement(userPurchaseKey, message.getQuantity());
            } else {
                log.warn("userPurchaseKey not exists, skip rollback: {}", userPurchaseKey);
            }

            log.info("Redis rolled back for purchase limit: concertId={}, gradeId={}, userId={}, quantity={}",
                     message.getConcertId(), message.getGradeId(), message.getUserId(), message.getQuantity());
        } catch (Exception e) {
            log.error("Failed to rollback Redis: orderNo={}", message.getOrderNo(), e);
            // Redis 回滚失败不影响主流程，由定时任务同步
        }
    }

    /**
     * 回填用户购买数量到 Redis
     * 当 DB 查到的真实数量与 Redis 不一致时，以 DB 为准修正 Redis
     */
    private void backfillUserPurchaseKey(Long userId, Long concertId, int totalCount) {
        try {
            String userPurchaseKey = RedisKeyConstants.buildUserConcertPurchaseKey(concertId, userId);
            redisUtils.setEx(userPurchaseKey, String.valueOf(totalCount), RedisExpireConstants.USER_PURCHASE_SECONDS);
            log.info("Backfilled userPurchaseKey: userId={}, concertId={}, count={}", userId, concertId, totalCount);
        } catch (Exception e) {
            log.error("Failed to backfill userPurchaseKey: userId={}, concertId={}", userId, concertId, e);
        }
    }

    /**
     * 获取消费幂等锁
     */
    private boolean acquireConsumeLock(String orderNo) {
        String idempotentKey = RedisKeyConstants.buildConsumeIdempotentKey(orderNo);
        Boolean setSuccess = redisUtils.setIfAbsent(idempotentKey, "1",
                RedisExpireConstants.CONSUME_IDEMPOTENT_HOURS, TimeUnit.HOURS);
        return setSuccess != null && setSuccess;
    }

    /**
     * 删除消费幂等锁
     */
    private void deleteConsumeLock(String orderNo) {
        String idempotentKey = RedisKeyConstants.buildConsumeIdempotentKey(orderNo);
        redisUtils.delete(idempotentKey);
    }

    /**
     * 创建处理中的订单
     */
    private void createProcessingOrder(TicketOrderMessage message) {
        CreateOrderQO qo = new CreateOrderQO(
                message.getOrderNo(),
                message.getUserId(),
                message.getConcertId(),
                message.getGradeId(),
                message.getQuantity(),
                message.getTotalPrice(),
                OrderStatus.PROCESSING.getCode(),
                null
        );
        orderServiceClient.createOrder(qo);
        log.info("Created processing order: {}", message.getOrderNo());
    }

    /**
     * 创建失败订单
     */
    private void createFailedOrder(TicketOrderMessage message, String failReason) {
        CreateOrderQO qo = new CreateOrderQO(
                message.getOrderNo(),
                message.getUserId(),
                message.getConcertId(),
                message.getGradeId(),
                message.getQuantity(),
                message.getTotalPrice(),
                OrderStatus.FAILED.getCode(),
                failReason
        );
        orderServiceClient.createOrder(qo);
        log.info("Created failed order: orderNo={}, reason={}", message.getOrderNo(), failReason);
    }

    /**
     * 更新订单状态为待支付（库存已扣减，等待用户付款）
     */
    private void updateOrderToPending(TicketOrderMessage message) {
        orderServiceClient.markOrderPending(message.getOrderNo());
        log.info("Updated order to PENDING: {}", message.getOrderNo());
    }

    /**
     * 标记订单失败
     */
    private void markOrderFailed(String orderNo, String reason) {
        try {
            orderServiceClient.markOrderFailed(orderNo, reason);
            log.info("Order marked as failed: orderNo={}, reason={}", orderNo, reason);
        } catch (Exception e) {
            log.error("Failed to mark order as failed: orderNo={}, reason={}", orderNo, reason, e);
        }
    }
}

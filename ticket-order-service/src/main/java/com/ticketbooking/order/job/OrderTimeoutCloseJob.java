package com.ticketbooking.order.job;

import com.ticketbooking.common.enums.OrderStatus;
import com.ticketbooking.common.enums.PaymentStatus;
import com.ticketbooking.common.model.dto.TradeQueryDTO;
import com.ticketbooking.order.client.PaymentServiceClient;
import com.ticketbooking.order.client.StockServiceClient;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.service.OrderService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时关单 Job
 * <p>
 * 扫描 PENDING 超过 30 分钟仍未支付的订单：
 * - 支付已是 SUCCESS → 对账修复，置 PAID
 * - 否则 → 关闭支付 + 回滚库存 + 置 CANCELLED
 * <p>
 * JobHandler: orderTimeoutClose，建议每分钟执行一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutCloseJob {

    private static final int TIMEOUT_MINUTES = 30;
    private static final int BATCH_SIZE = 200;

    private final OrderService orderService;
    private final PaymentServiceClient paymentServiceClient;
    private final StockServiceClient stockServiceClient;

    @XxlJob("orderTimeoutClose")
    public void orderTimeoutClose() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        List<Order> staleOrders = orderService.findStalePendingOrders(cutoff, BATCH_SIZE);

        if (staleOrders.isEmpty()) {
            return;
        }
        log.info("Order timeout close job started: count={}", staleOrders.size());

        int reconciled = 0;
        int closed = 0;
        int failed = 0;

        for (Order order : staleOrders) {
            try {
                if (handleOne(order)) {
                    reconciled++;
                } else {
                    closed++;
                }
            } catch (Exception e) {
                log.error("Timeout close failed: orderNo={}", order.getOrderNo(), e);
                failed++;
            }
        }

        log.info("Order timeout close job finished: reconciled={}, closed={}, failed={}",
                reconciled, closed, failed);
    }

    /**
     * @return true 表示对账修复为 PAID，false 表示已关单取消
     */
    private boolean handleOne(Order order) {
        String orderNo = order.getOrderNo();

        // 1. 查询支付状态：已 SUCCESS 则对账修复
        if (order.getPayChannel() != null) {
            try {
                TradeQueryDTO trade = paymentServiceClient.query(orderNo, order.getPayChannel());
                if (trade != null && trade.getStatus() == PaymentStatus.SUCCESS) {
                    orderService.markOrderPaid(orderNo);
                    log.info("Reconciled order to PAID: orderNo={}", orderNo);
                    return true;
                }
            } catch (Exception e) {
                // 查询失败按未支付处理，走关单流程
                log.warn("Query payment status failed, proceed to close: orderNo={}, error={}", orderNo, e.getMessage());
            }
        }

        // 2. 关闭支付（尽力而为，忽略已关闭/已支付的响应）
        if (order.getPayChannel() != null) {
            try {
                paymentServiceClient.close(orderNo, order.getPayChannel());
            } catch (Exception e) {
                log.warn("Close payment failed (ignore and continue): orderNo={}, error={}", orderNo, e.getMessage());
            }
        }

        // 3. 回滚库存
        //    PENDING：消费者已扣减 DB，回滚 DB + Redis
        //    PROCESSING：Kafka 尚未成功消费、DB 未扣减，仅回补 Redis 预扣减（迟到的重试由消费者状态校验幂等跳过）
        if (order.getStatus() != null && order.getStatus() == OrderStatus.PROCESSING.getCode()) {
            orderService.rollbackRedisStockOnly(order);
        } else {
            stockServiceClient.restoreStock(order.getConcertId(), order.getGradeId(),
                    order.getQuantity(), orderNo);
        }

        // 4. 置 CANCELLED
        orderService.markOrderCancelled(orderNo, "支付超时自动取消");
        log.info("Order cancelled by timeout: orderNo={}", orderNo);
        return false;
    }
}

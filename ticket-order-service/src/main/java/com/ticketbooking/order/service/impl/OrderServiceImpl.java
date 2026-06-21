package com.ticketbooking.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.constant.RedisExpireConstants;
import com.ticketbooking.common.constant.RedisKeyConstants;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.OrderStatus;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.model.dto.*;
import com.ticketbooking.common.model.qo.CreateOrderQO;
import com.ticketbooking.common.model.qo.PayRequestQO;
import com.ticketbooking.common.model.qo.RefundRequestQO;
import com.ticketbooking.common.mq.TicketOrderMessage;
import com.ticketbooking.common.utils.RedisUtils;
import com.ticketbooking.order.client.PaymentServiceClient;
import com.ticketbooking.order.client.StockServiceClient;
import com.ticketbooking.order.client.TicketServiceClient;
import com.ticketbooking.order.config.BookingLuaScript;
import com.ticketbooking.order.converter.OrderConverter;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.mapper.OrderMapper;
import com.ticketbooking.order.model.dto.TicketInfoDTO;
import com.ticketbooking.order.model.qo.InitiatePayQO;
import com.ticketbooking.order.model.vo.OrderVO;
import com.ticketbooking.order.mq.KafkaProducerService;
import com.ticketbooking.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final KafkaProducerService kafkaProducerService;
    private final RedisUtils redisUtils;
    private final RedissonClient redissonClient;
    private final StockServiceClient stockServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final BookingLuaScript bookingLuaScript;
    private final ObjectMapper objectMapper;
    private final OrderConverter orderConverter;
    private final TicketServiceClient ticketServiceClient;

    @Override
    public String createOrder(Long userId, Long concertId, Long gradeId, Integer quantity) {
        log.info("Creating order: userId={}, concertId={}, gradeId={}, quantity={}",
                userId, concertId, gradeId, quantity);

        TicketInfoDTO ticketInfo = getTicketInfoWithLock(concertId, gradeId);
        if (ticketInfo == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        log.info("Ticket info loaded: concertId={}, gradeId={}, price={}, stock={}",
                concertId, gradeId, ticketInfo.getPrice(), ticketInfo.getAvailableStock());

        Long result = executeBookingWithRetry(userId, concertId, gradeId, quantity);

        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_BUSY);
        }

        return switch (result.intValue()) {
            case -2 -> throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
            case -3 -> throw new BusinessException(ErrorCode.TICKET_SOLD_OUT);
            case -4 -> throw new BusinessException(ErrorCode.SYSTEM_BUSY, "限购配置不存在");
            case -5 -> throw new BusinessException(ErrorCode.ALREADY_BOUGHT, "超出限购数量");
            case 1 -> processOrderAsync(userId, concertId, gradeId, quantity, ticketInfo);
            default -> throw new BusinessException(ErrorCode.SYSTEM_BUSY);
        };
    }

    /**
     * 执行抢票 Lua 脚本，key 缺失时从 DB 回填后重试一次
     */
    private Long executeBookingWithRetry(Long userId, Long concertId, Long gradeId, Integer quantity) {
        DefaultRedisScript<Long> script = bookingLuaScript.getBookingScript();
        List<String> keys = Arrays.asList(
                RedisKeyConstants.buildTicketStockHashKey(concertId),
                RedisKeyConstants.buildUserConcertPurchaseKey(concertId, userId),
                RedisKeyConstants.buildConcertLimitKey(concertId)
        );
        String[] args = {
                String.valueOf(userId), String.valueOf(quantity),
                String.valueOf(gradeId), String.valueOf(RedisExpireConstants.USER_PURCHASE_SECONDS)
        };

        Long result = redisUtils.executeLuaScript(script, keys, args);
        log.info("Lua script result: {} ({})", result, BookingLuaScript.getResultDesc(result));

        if (result == -2 || result == -4) {
            log.info("Lua indicates missing keys (result={}), recovering: concertId={}, gradeId={}", result, concertId, gradeId);
            result = recoverBookingKeys(concertId, gradeId, script, keys, args);
        }

        return result;
    }

    /**
     * 恢复缺失的 booking key，加锁防止缓存击穿
     * 拿到锁的请求从 DB 回填并重试 Lua，拿不到锁的直接返回原始错误码
     */
    private Long recoverBookingKeys(Long concertId, Long gradeId, DefaultRedisScript<Long> script, List<String> keys, String[] args) {
        String lockKey = RedisKeyConstants.buildTicketLockKey(concertId, gradeId);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(1, 5, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Recovery lock not acquired, returning original error: concertId={}, gradeId={}", concertId, gradeId);
                return (long) -2;
            }

            try {
                // double-check：拿到锁后重新执行一次 Lua，可能其他请求已经回填了
                Long retryResult = redisUtils.executeLuaScript(script, keys, args);
                if (retryResult != null && retryResult == 1) {
                    log.info("Keys already recovered by another request: concertId={}, gradeId={}", concertId, gradeId);
                    return retryResult;
                }

                // 确认仍需回填，从 DB 加载
                StockDTO stock = stockServiceClient.getStock(concertId, gradeId);
                if (stock != null) {
                    syncTicketDataToRedis(concertId, gradeId, stock);
                    retryResult = redisUtils.executeLuaScript(script, keys, args);
                    log.info("Lua retry after recovery: {} ({})", retryResult, BookingLuaScript.getResultDesc(retryResult));
                    return retryResult;
                }
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Recovery lock interrupted: concertId={}, gradeId={}", concertId, gradeId, e);
        } catch (Exception e) {
            log.error("Failed to recover booking keys: concertId={}, gradeId={}", concertId, gradeId, e);
        }
        return (long) -2;
    }

    private TicketInfoDTO getTicketInfoWithLock(Long concertId, Long gradeId) {
        String cacheKey = RedisKeyConstants.buildConcertInfoKey(concertId) + ":" + gradeId;

        String cachedInfo = redisUtils.get(cacheKey);
        if (cachedInfo != null) {
            return parseTicketInfo(cachedInfo);
        }

        String lockKey = RedisKeyConstants.buildTicketLockKey(concertId, gradeId);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Failed to acquire lock for ticket info: concertId={}, gradeId={}", concertId, gradeId);
                throw new BusinessException(ErrorCode.SYSTEM_BUSY);
            }

            try {
                cachedInfo = redisUtils.get(cacheKey);
                if (cachedInfo != null) {
                    log.info("Cache hit after acquiring lock: concertId={}, gradeId={}", concertId, gradeId);
                    return parseTicketInfo(cachedInfo);
                }

                TicketInfoDTO ticketInfo = loadTicketInfoFromDb(concertId, gradeId);
                if (ticketInfo != null) {
                    saveTicketInfoToCache(cacheKey, ticketInfo);
                    log.info("Ticket info loaded from DB and cached: concertId={}, gradeId={}", concertId, gradeId);
                }

                return ticketInfo;
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock interrupted for concertId={}, gradeId={}", concertId, gradeId, e);
            throw new BusinessException(ErrorCode.SYSTEM_BUSY);
        }
    }

    private TicketInfoDTO loadTicketInfoFromDb(Long concertId, Long gradeId) {
        try {
            StockDTO stock = stockServiceClient.getStock(concertId, gradeId);
            if (stock == null) {
                log.warn("Stock not found in DB: concertId={}, gradeId={}", concertId, gradeId);
                return null;
            }

            // 同步写入 stock 和 limit key 到 Redis（作为预热数据过期后的兜底）
            syncTicketDataToRedis(concertId, gradeId, stock);

            return TicketInfoDTO.builder()
                    .concertId(concertId)
                    .gradeId(gradeId)
                    .concertName(stock.getConcertName())
                    .gradeName(stock.getGradeName())
                    .price(stock.getPrice())
                    .availableStock(stock.getAvailableStock())
                    .purchaseLimit(stock.getPurchaseLimit())
                    .build();
        } catch (Exception e) {
            log.error("Failed to load ticket info from DB: concertId={}, gradeId={}", concertId, gradeId, e);
            return null;
        }
    }

    /**
     * 同步票务数据到 Redis（库存和限购数量）
     * 只写 field 值，不覆盖已有 key 的 TTL（TTL 由预热统一管理）
     * 仅在 key 不存在时（预热未执行）设置默认 TTL
     */
    private void syncTicketDataToRedis(Long concertId, Long gradeId, StockDTO stock) {
        // 写入库存 Hash field
        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(concertId);
        Boolean hashExists = redisUtils.hasKey(stockHashKey);
        redisUtils.hSet(stockHashKey, String.valueOf(gradeId), String.valueOf(stock.getAvailableStock()));
        if (!Boolean.TRUE.equals(hashExists)) {
            redisUtils.expire(stockHashKey, RedisExpireConstants.PREHEAT_CACHE_SECONDS, TimeUnit.SECONDS);
        }

        // 写入限购 key：已存在不覆盖
        String limitKey = RedisKeyConstants.buildConcertLimitKey(concertId);
        if (!Boolean.TRUE.equals(redisUtils.hasKey(limitKey))) {
            int purchaseLimit = stock.getPurchaseLimit() != null ? stock.getPurchaseLimit() : 1;
            redisUtils.setEx(limitKey, String.valueOf(purchaseLimit), RedisExpireConstants.PREHEAT_CACHE_SECONDS);
        }

        log.info("Synced ticket data to Redis: concertId={}, gradeId={}, stock={}, hashExisted={}",
                concertId, gradeId, stock.getAvailableStock(), hashExists);
    }

    private void saveTicketInfoToCache(String cacheKey, TicketInfoDTO ticketInfo) {
        try {
            String jsonValue = objectMapper.writeValueAsString(ticketInfo);
            // 缓存过期时间：1小时，与其他 key 同步
            redisUtils.setEx(cacheKey, jsonValue, 3600);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ticket info", e);
        }
    }

    private String processOrderAsync(Long userId, Long concertId, Long gradeId, Integer quantity, TicketInfoDTO ticketInfo) {
        String orderNo = generateOrderNo();
        Integer totalPrice = ticketInfo.getPrice() * quantity;

        // 先同步落库一条 PROCESSING 订单：保证下单接口返回后立即可查（支付/详情不再 404），
        // 也为超时关单 Job 留下行（即使 Kafka 链路全断也有单可兜底）。库存语义不变——仍在 Lua 预扣成功之后。
        createOrderFromStock(orderNo, userId, concertId, gradeId, quantity, totalPrice,
                OrderStatus.PROCESSING.getCode(), null);

        TicketOrderMessage message = new TicketOrderMessage(orderNo, userId, concertId, gradeId, quantity, totalPrice);
        kafkaProducerService.sendOrderMessage(message);

        log.info("Order created: orderNo={}, userId={}, concertId={}, gradeId={}, quantity={}",
                orderNo, userId, concertId, gradeId, quantity);

        return orderNo;
    }

    @Override
    public Order findByOrderNo(String orderNo) {
        Order order = getOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return list(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime));
    }

    public Order createOrderFromStock(String orderNo, Long userId, Long concertId, Long gradeId,
                                      Integer quantity, Integer totalPrice, Integer status, String failReason) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setConcertId(concertId);
        order.setGradeId(gradeId);
        order.setQuantity(quantity);
        order.setTotalPrice(totalPrice);
        order.setStatus(status);
        order.setFailReason(failReason);
        save(order);

        log.info("Order created from stock service: orderNo={}, status={}, failReason={}", orderNo, status, failReason);
        return order;
    }

    private String generateOrderNo() {
        return LocalDateTime.now().format(ORDER_NO_FORMATTER)
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private TicketInfoDTO parseTicketInfo(String cachedInfo) {
        if (cachedInfo == null || cachedInfo.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(cachedInfo, TicketInfoDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse ticket info: {}", cachedInfo, e);
            return null;
        }
    }

    @Override
    public OrderDTO findDTOByOrderNo(String orderNo) {
        Order order = getOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        // 交给上游自己处理
        if (order == null) {
            return null;
        }
        return convertToDTO(order);
    }

    @Override
    public OrderDTO createOrderDTO(CreateOrderQO qo) {
        Order order = createOrderFromStock(
                qo.getOrderNo(),
                qo.getUserId(),
                qo.getConcertId(),
                qo.getGradeId(),
                qo.getQuantity(),
                qo.getTotalPrice(),
                qo.getStatus(),
                qo.getFailReason()
        );
        return convertToDTO(order);
    }

    @Override
    public void markOrderFailed(String orderNo, String failReason) {
        Order order = getOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order != null) {
            order.setStatus(OrderStatus.FAILED.getCode());
            order.setFailReason(failReason);
            updateById(order);
            log.info("Order marked as failed: orderNo={}, reason={}", orderNo, failReason);
        } else {
            log.warn("Order not found when marking failed: orderNo={}", orderNo);
        }
    }

    @Override
    public void markOrderPending(String orderNo) {
        Order order = getOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order != null) {
            order.setStatus(OrderStatus.PENDING.getCode());
            updateById(order);
            log.info("Order marked as pending: orderNo={}", orderNo);
        } else {
            log.warn("Order not found when marking pending: orderNo={}", orderNo);
        }
    }

    @Override
    public void markOrderPaid(String orderNo) {
        Order order = getOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order != null) {
            // 幂等：已经是 PAID 就直接返回，避免重复支付回调覆盖 payTime
            if (order.getStatus() != null && order.getStatus() == OrderStatus.PAID.getCode()) {
                return;
            }
            order.setStatus(OrderStatus.PAID.getCode());
            order.setPayTime(LocalDateTime.now());
            updateById(order);
            log.info("Order marked as paid: orderNo={}", orderNo);
        } else {
            log.warn("Order not found when marking paid: orderNo={}", orderNo);
        }
    }

    @Override
    public void markOrderCancelled(String orderNo, String reason) {
        Order order = getOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            log.warn("Order not found when marking cancelled: orderNo={}", orderNo);
            return;
        }
        order.setStatus(OrderStatus.CANCELLED.getCode());
        order.setFailReason(reason);
        updateById(order);
        // 回滚 Redis 用户购买计数（取消/退款都应回退限购计数）
        rollbackRedisUserPurchase(order);
        log.info("Order marked as cancelled: orderNo={}, reason={}", orderNo, reason);
    }

    @Override
    public PayResponseDTO initiatePayment(Long userId, String orderNo, InitiatePayQO qo) {
        Order order = findByOrderNo(orderNo);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.PENDING.getCode()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_PAYABLE, "订单当前状态不可支付");
        }
        // 30 分钟支付窗口（与 PaymentRecord.expireTime 一致）
        if (order.getCreateTime() != null
                && order.getCreateTime().plusMinutes(30).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_PAYABLE, "订单已超时，请重新下单");
        }

        // 记录支付渠道（供超时关单 Job 查询支付状态用）
        if (qo.getChannel() != null && !qo.getChannel().equals(order.getPayChannel())) {
            order.setPayChannel(qo.getChannel());
            updateById(order);
        }

        PayRequestQO payRequest = new PayRequestQO();
        payRequest.setOrderNo(orderNo);
        payRequest.setAmount(order.getTotalPrice());
        payRequest.setSubject("订单支付");
        payRequest.setChannel(qo.getChannel());
        payRequest.setPayMode(qo.getPayMode());
        payRequest.setOpenId(qo.getOpenId());
        payRequest.setReturnUrl(qo.getReturnUrl());

        return paymentServiceClient.prepay(payRequest);
    }

    @Override
    public void cancelAndRefund(String orderNo) {
        Order order = findByOrderNo(orderNo);
        if (order.getStatus() == null || order.getStatus() != OrderStatus.PAID.getCode()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_NOT_ALLOWED, "仅已支付订单可退款");
        }

        RefundRequestQO refundRequest = new RefundRequestQO();
        refundRequest.setOrderNo(orderNo);
        refundRequest.setRefundNo("RF" + orderNo);
        refundRequest.setRefundAmount(order.getTotalPrice());
        refundRequest.setTotalAmount(order.getTotalPrice());
        refundRequest.setReason("管理员取消订单退款");
        refundRequest.setChannel(order.getPayChannel());

        paymentServiceClient.refund(refundRequest);

        // 退款成功后回滚 DB + Redis 库存（Redis 用户购买计数由 markOrderCancelled 回滚）
        stockServiceClient.restoreStock(order.getConcertId(), order.getGradeId(),
                order.getQuantity(), orderNo);

        markOrderCancelled(orderNo, "管理员取消订单并退款");
    }

    /**
     * 回滚 Redis 用户购买计数（退款/取消时调用，仅当 key 存在时）
     */
    private void rollbackRedisUserPurchase(Order order) {
        try {
            String userPurchaseKey = RedisKeyConstants.buildUserConcertPurchaseKey(
                    order.getConcertId(), order.getUserId());
            if (Boolean.TRUE.equals(redisUtils.hasKey(userPurchaseKey))) {
                redisUtils.decrement(userPurchaseKey, order.getQuantity());
            }
        } catch (Exception e) {
            log.error("Failed to rollback Redis user purchase: orderNo={}", order.getOrderNo(), e);
        }
    }

    @Override
    public void rollbackRedisStockOnly(Order order) {
        // PROCESSING 订单：Kafka 尚未成功消费、DB 未扣减，只回补 Lua 预扣减的 Redis 库存 Hash。
        // 注意：仅当 field 存在时回补，避免 HINCRBY 误创建。
        try {
            String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(order.getConcertId());
            String field = String.valueOf(order.getGradeId());
            if (Boolean.TRUE.equals(redisUtils.hExists(stockHashKey, field))) {
                redisUtils.hIncrBy(stockHashKey, field, order.getQuantity());
                log.info("Redis stock rolled back (PROCESSING close): concertId={}, gradeId={}, quantity={}, orderNo={}",
                        order.getConcertId(), order.getGradeId(), order.getQuantity(), order.getOrderNo());
            } else {
                log.warn("stockHash field not exists, skip Redis rollback: concertId={}, gradeId={}, orderNo={}",
                        order.getConcertId(), order.getGradeId(), order.getOrderNo());
            }
        } catch (Exception e) {
            log.error("Failed to rollback Redis stock: orderNo={}", order.getOrderNo(), e);
        }
    }

    @Override
    public List<Order> findStalePendingOrders(LocalDateTime before, int limit) {
        return list(new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, OrderStatus.PROCESSING.getCode(), OrderStatus.PENDING.getCode())
                .lt(Order::getCreateTime, before)
                .orderByAsc(Order::getCreateTime)
                .last("LIMIT " + limit));
    }

    private OrderDTO convertToDTO(Order order) {
        OrderDTO dto = new OrderDTO();
        BeanUtils.copyProperties(order, dto);
        return dto;
    }

    @Override
    public PageResult<OrderVO> getOrderPage(Long current, Long size, Long userId, Integer status, String orderNo) {
        Page<Order> page = new Page<>(current, size);

        IPage<Order> orderPage = page(page,
                new LambdaQueryWrapper<Order>()
                        .eq(userId != null, Order::getUserId, userId)
                        .eq(status != null, Order::getStatus, status)
                        .eq(orderNo != null, Order::getOrderNo, orderNo)
                        .orderByDesc(Order::getCreateTime)
        );

        List<OrderVO> orderVOs = orderConverter.toVOList(orderPage.getRecords());
        fillConcertAndGradeNames(orderVOs);

        return PageResult.of(
                orderVOs,
                orderPage.getTotal(),
                orderPage.getCurrent(),
                orderPage.getSize()
        );
    }

    @Override
    public OrderVO getOrderVOByOrderNo(String orderNo) {
        Order order = findByOrderNo(orderNo);
        OrderVO vo = orderConverter.toVO(order);
        fillConcertAndGradeName(vo);
        return vo;
    }

    @Override
    public List<OrderVO> getOrderVOsByUserId(Long userId) {
        List<Order> orders = findByUserId(userId);
        List<OrderVO> orderVOs = orderConverter.toVOList(orders);
        fillConcertAndGradeNames(orderVOs);
        return orderVOs;
    }

    private void fillConcertAndGradeNames(List<OrderVO> orderVOs) {
        List<Long> gradeIds = orderVOs.stream()
                .map(OrderVO::getGradeId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, TicketGradeDTO> gradeInfoMap = gradeIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> {
                            try {
                                return ticketServiceClient.getGradeById(id);
                            } catch (Exception e) {
                                return new TicketGradeDTO();
                            }
                        }
                ));

        orderVOs.forEach(vo -> {
            TicketGradeDTO gradeInfo = gradeInfoMap.get(vo.getGradeId());
            if (gradeInfo != null) {
                vo.setConcertName(gradeInfo.getConcertName());
                vo.setGradeName(gradeInfo.getGradeName());
            }
        });
    }

    private void fillConcertAndGradeName(OrderVO vo) {
        try {
            TicketGradeDTO gradeInfo = ticketServiceClient.getGradeById(vo.getGradeId());
            if (gradeInfo != null) {
                vo.setConcertName(gradeInfo.getConcertName());
                vo.setGradeName(gradeInfo.getGradeName());
            }
        } catch (Exception e) {
            // Ignore if service is unavailable
        }
    }

    @Override
    public PageResult<OrderVO> getOrderPageByUserId(Long userId, Long current, Long size, Integer status) {
        return getOrderPage(current, size, userId, status, null);
    }

    @Override
    public DashboardStatsDTO getDashboardStats() {
        DashboardStatsDTO stats = new DashboardStatsDTO();

        // 订单总数（统计已支付订单）
        LambdaQueryWrapper<Order> paidCountWrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, 2);
        long totalOrders = count(paidCountWrapper);
        stats.setTotalOrders((int) totalOrders);

        // 总收入（只统计已支付订单，status=2）
        LambdaQueryWrapper<Order> paidWrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, 2);
        List<Order> paidOrders = list(paidWrapper);
        long totalRevenue = paidOrders.stream()
                .mapToLong(o -> o.getTotalPrice() != null ? o.getTotalPrice() : 0)
                .sum();
        stats.setTotalRevenue(totalRevenue);

        // 今日订单（已支付）
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LambdaQueryWrapper<Order> todayWrapper = new LambdaQueryWrapper<Order>()
                .ge(Order::getCreateTime, todayStart)
                .eq(Order::getStatus, 2);
        long todayOrders = count(todayWrapper);
        stats.setTodayOrders((int) todayOrders);

        // 今日收入（已支付订单，status=2）
        LambdaQueryWrapper<Order> todayPaidWrapper = new LambdaQueryWrapper<Order>()
                .ge(Order::getCreateTime, todayStart)
                .eq(Order::getStatus, 2);
        List<Order> todayPaidOrders = list(todayPaidWrapper);
        long todayRevenue = todayPaidOrders.stream()
                .mapToLong(o -> o.getTotalPrice() != null ? o.getTotalPrice() : 0)
                .sum();
        stats.setTodayRevenue(todayRevenue);

        log.info("Dashboard stats: totalOrders={}, totalRevenue={}, todayOrders={}, todayRevenue={}",
                totalOrders, totalRevenue, todayOrders, todayRevenue);

        return stats;
    }

    @Override
    public boolean hasUserBought(Long userId, Long concertId, Long gradeId) {
        // 查询是否存在已支付的订单
        long count = count(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getConcertId, concertId)
                .eq(Order::getGradeId, gradeId)
                .eq(Order::getStatus, OrderStatus.PAID.getCode()));
        return count > 0;
    }

    @Override
    public int countUserPurchased(Long userId, Long concertId) {
        // 统计用户在该演唱会的已支付订单总票数
        List<Order> orders = list(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getConcertId, concertId)
                .eq(Order::getStatus, OrderStatus.PAID.getCode()));

        return orders.stream()
                .mapToInt(o -> o.getQuantity() != null ? o.getQuantity() : 0)
                .sum();
    }

    @Override
    public List<SalesDataDTO> getSalesData(Integer days) {
        List<SalesDataDTO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            // 统计当天已支付订单
            LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                    .ge(Order::getCreateTime, dayStart)
                    .lt(Order::getCreateTime, dayEnd)
                    .eq(Order::getStatus, OrderStatus.PAID.getCode());

            List<Order> dayOrders = list(wrapper);

            SalesDataDTO dto = new SalesDataDTO();
            dto.setDate(date.format(formatter));
            dto.setOrders(dayOrders.size());
            dto.setRevenue(dayOrders.stream()
                    .mapToLong(o -> o.getTotalPrice() != null ? o.getTotalPrice() : 0L)
                    .sum());
            result.add(dto);
        }

        return result;
    }

    @Override
    public List<ConcertSalesDTO> getConcertSalesStats() {
        List<ConcertSalesDTO> result = new ArrayList<>();

        // 按演唱会分组统计已支付订单
        List<Order> paidOrders = list(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PAID.getCode()));

        Map<Long, List<Order>> ordersByConcert = paidOrders.stream()
                .collect(Collectors.groupingBy(Order::getConcertId));

        ordersByConcert.forEach((concertId, orders) -> {
            ConcertSalesDTO dto = new ConcertSalesDTO();
            dto.setConcertId(concertId);
            dto.setTotalOrders(orders.size());
            dto.setTotalTickets(orders.stream()
                    .mapToInt(o -> o.getQuantity() != null ? o.getQuantity() : 0)
                    .sum());
            dto.setTotalRevenue(orders.stream()
                    .mapToLong(o -> o.getTotalPrice() != null ? o.getTotalPrice() : 0L)
                    .sum());
            result.add(dto);
        });

        // 按总收入降序排序
        result.sort((a, b) -> Long.compare(b.getTotalRevenue(), a.getTotalRevenue()));

        return result;
    }
}

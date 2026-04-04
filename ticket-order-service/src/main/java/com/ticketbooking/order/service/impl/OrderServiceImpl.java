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
import com.ticketbooking.common.model.dto.ConcertSalesDTO;
import com.ticketbooking.common.model.dto.DashboardStatsDTO;
import com.ticketbooking.common.model.dto.OrderDTO;
import com.ticketbooking.common.model.dto.SalesDataDTO;
import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.common.model.dto.TicketGradeDTO;
import com.ticketbooking.common.model.qo.CreateOrderQO;
import com.ticketbooking.common.mq.TicketOrderMessage;
import com.ticketbooking.common.utils.RedisUtils;
import com.ticketbooking.order.client.StockServiceClient;
import com.ticketbooking.order.client.TicketServiceClient;
import com.ticketbooking.order.config.BookingLuaScript;
import com.ticketbooking.order.converter.OrderConverter;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.mapper.OrderMapper;
import com.ticketbooking.order.model.dto.TicketInfoDTO;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final BookingLuaScript bookingLuaScript;
    private final ObjectMapper objectMapper;
    private final OrderConverter orderConverter;
    private final TicketServiceClient ticketServiceClient;

    @Override
    public String createOrder(Long userId, Long concertId, Long gradeId, Integer quantity) {
        // 使用 Hash 结构的库存 Key
        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(concertId);
        String userPurchaseKey = RedisKeyConstants.buildUserConcertPurchaseKey(concertId, userId);
        String limitKey = RedisKeyConstants.buildConcertLimitKey(concertId);

        log.info("Creating order: userId={}, concertId={}, gradeId={}, quantity={}",
                userId, concertId, gradeId, quantity);

        TicketInfoDTO ticketInfo = getTicketInfoWithLock(concertId, gradeId);
        if (ticketInfo == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        log.info("Ticket info loaded: concertId={}, gradeId={}, price={}, stock={}", concertId, gradeId, ticketInfo.getPrice(), ticketInfo.getAvailableStock());

        DefaultRedisScript<Long> script = bookingLuaScript.getBookingScript();
        // KEYS: stockHashKey, userPurchaseKey, limitKey
        List<String> keys = Arrays.asList(stockHashKey, userPurchaseKey, limitKey);
        // ARGV: userId, quantity, gradeId, expireSeconds
        Long result = redisUtils.executeLuaScript(script, keys,
                String.valueOf(userId), String.valueOf(quantity), String.valueOf(gradeId),
                String.valueOf(RedisExpireConstants.USER_PURCHASE_SECONDS));

        log.info("Lua script result: {} ({})", result, BookingLuaScript.getResultDesc(result));

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
     * 库存使用 Hash 结构写入
     */
    private void syncTicketDataToRedis(Long concertId, Long gradeId, StockDTO stock) {
        // 使用兜底缓存过期时间（比预热缓存短）
        long expireSeconds = RedisExpireConstants.FALLBACK_CACHE_SECONDS;

        // 写入库存 Hash
        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(concertId);
        redisUtils.hSet(stockHashKey, String.valueOf(gradeId), String.valueOf(stock.getAvailableStock()));
        // 为整个 Hash 设置过期时间（如果 key 存在）
        if (Boolean.TRUE.equals(redisUtils.hasKey(stockHashKey))) {
            redisUtils.expire(stockHashKey, expireSeconds, TimeUnit.SECONDS);
        }

        // 写入限购 key
        String limitKey = RedisKeyConstants.buildConcertLimitKey(concertId);
        int purchaseLimit = stock.getPurchaseLimit() != null ? stock.getPurchaseLimit() : 1;
        redisUtils.setEx(limitKey, String.valueOf(purchaseLimit), expireSeconds);

        log.info("Synced ticket data to Redis: concertId={}, gradeId={}, stock={}, limit={}",
                concertId, gradeId, stock.getAvailableStock(), purchaseLimit);
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
    public void markOrderPaid(String orderNo) {
        Order order = getOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order != null) {
            order.setStatus(OrderStatus.PAID.getCode());
            updateById(order);
            log.info("Order marked as paid: orderNo={}", orderNo);
        } else {
            log.warn("Order not found when marking paid: orderNo={}", orderNo);
        }
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

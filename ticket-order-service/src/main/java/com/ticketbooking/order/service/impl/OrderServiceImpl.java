package com.ticketbooking.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.constant.RedisKeyConstants;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.model.dto.OrderDTO;
import com.ticketbooking.common.mq.TicketOrderMessage;
import com.ticketbooking.common.utils.RedisUtils;
import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.order.client.StockServiceClient;
import com.ticketbooking.order.config.BookingLuaScript;
import com.ticketbooking.order.model.dto.TicketInfoDTO;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.mapper.OrderMapper;
import com.ticketbooking.order.mq.OrderMessageProducer;
import com.ticketbooking.order.service.OrderService;
import com.ticketbooking.order.converter.OrderConverter;
import com.ticketbooking.order.client.TicketServiceClient;
import com.ticketbooking.order.model.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private static final long IDEMPOTENT_EXPIRE_SECONDS = 24 * 3600;
    private static final long LOCK_WAIT_TIME = 3;
    private static final long LOCK_LEASE_TIME = 10;

    private final OrderMessageProducer orderMessageProducer;
    private final RedisUtils redisUtils;
    private final RedissonClient redissonClient;
    private final StockServiceClient stockServiceClient;
    private final BookingLuaScript bookingLuaScript;
    private final ObjectMapper objectMapper;
    private final OrderConverter orderConverter;
    private final TicketServiceClient ticketServiceClient;

    @Override
    public String createOrder(Long userId, Long concertId, Long gradeId, Integer quantity) {
        String stockKey = RedisKeyConstants.buildTicketStockKey(concertId, gradeId);
        String userTicketKey = RedisKeyConstants.buildUserTicketKey(concertId, gradeId, userId);

        log.info("Creating order: userId={}, concertId={}, gradeId={}, quantity={}",
                userId, concertId, gradeId, quantity);

        TicketInfoDTO ticketInfo = getTicketInfoWithLock(concertId, gradeId);
        if (ticketInfo == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        log.info("Ticket info loaded: concertId={}, gradeId={}, price={}, stock={}", concertId, gradeId, ticketInfo.getPrice(), ticketInfo.getAvailableStock());

        DefaultRedisScript<Long> script = bookingLuaScript.getBookingScript();
        List<String> keys = Arrays.asList(stockKey, userTicketKey);
        Long result = redisUtils.executeLuaScript(script, keys, String.valueOf(userId), String.valueOf(quantity));

        log.info("Lua script result: {}", result);

        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_BUSY);
        }

        switch (result.intValue()) {
            case -1:
                throw new BusinessException(ErrorCode.ALREADY_BOUGHT);
            case -2:
                throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
            case -3:
                throw new BusinessException(ErrorCode.TICKET_SOLD_OUT);
            case 1:
                return processOrderAsync(userId, concertId, gradeId, quantity, ticketInfo);
            default:
                throw new BusinessException(ErrorCode.SYSTEM_BUSY);
        }
    }

    private TicketInfoDTO getTicketInfoWithLock(Long concertId, Long gradeId) {
        String cacheKey = RedisKeyConstants.buildConcertInfoKey(concertId) + ":" + gradeId;

        String cachedInfo = redisUtils.get(cacheKey);
        if (cachedInfo != null) {
            return parseTicketInfo(cachedInfo);
        }

        String lockKey = "lock:ticket:info:" + concertId + ":" + gradeId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
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

            return TicketInfoDTO.builder()
                    .concertId(concertId)
                    .gradeId(gradeId)
                    .concertName(stock.getConcertName())
                    .gradeName(stock.getGradeName())
                    .price(stock.getPrice())
                    .availableStock(stock.getAvailableStock())
                    .build();
        } catch (Exception e) {
            log.error("Failed to load ticket info from DB: concertId={}, gradeId={}", concertId, gradeId, e);
            return null;
        }
    }

    private void saveTicketInfoToCache(String cacheKey, TicketInfoDTO ticketInfo) {
        try {
            String jsonValue = objectMapper.writeValueAsString(ticketInfo);
            redisUtils.set(cacheKey, jsonValue);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ticket info", e);
        }
    }

    private String processOrderAsync(Long userId, Long concertId, Long gradeId, Integer quantity, TicketInfoDTO ticketInfo) {
        String orderNo = generateOrderNo();
        Integer totalPrice = ticketInfo.getPrice() * quantity;

        String idempotentKey = RedisKeyConstants.buildOrderIdempotentKey(orderNo);
        redisUtils.setEx(idempotentKey, "PROCESSING", IDEMPOTENT_EXPIRE_SECONDS);

        TicketOrderMessage message = new TicketOrderMessage(orderNo, userId, concertId, gradeId, quantity, totalPrice);
        orderMessageProducer.sendOrderMessage(message);

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

    @Override
    public Order createOrderFromStock(String orderNo, Long userId, Long concertId, Long gradeId,
                                      Integer quantity, Integer totalPrice, Integer status) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setConcertId(concertId);
        order.setGradeId(gradeId);
        order.setQuantity(quantity);
        order.setTotalPrice(totalPrice);
        order.setStatus(status);
        save(order);

        log.info("Order created from stock service: orderNo={}, status={}", orderNo, status);
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
    public OrderDTO createOrderDTO(com.ticketbooking.common.model.qo.CreateOrderQO qo) {
        Order order = createOrderFromStock(
                qo.getOrderNo(),
                qo.getUserId(),
                qo.getConcertId(),
                qo.getGradeId(),
                qo.getQuantity(),
                qo.getTotalPrice(),
                qo.getStatus()
        );
        return convertToDTO(order);
    }

    private OrderDTO convertToDTO(Order order) {
        OrderDTO dto = new OrderDTO();
        BeanUtils.copyProperties(order, dto);
        return dto;
    }

    @Override
    public com.ticketbooking.common.model.PageResult<OrderVO> getOrderPage(Long current, Long size, Long userId, Integer status, String orderNo) {
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

        return com.ticketbooking.common.model.PageResult.of(
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

        Map<Long, com.ticketbooking.common.model.dto.TicketGradeDTO> gradeInfoMap = gradeIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> {
                            try {
                                return ticketServiceClient.getGradeById(id);
                            } catch (Exception e) {
                                return new com.ticketbooking.common.model.dto.TicketGradeDTO();
                            }
                        }
                ));

        orderVOs.forEach(vo -> {
            com.ticketbooking.common.model.dto.TicketGradeDTO gradeInfo = gradeInfoMap.get(vo.getGradeId());
            if (gradeInfo != null) {
                vo.setConcertName(gradeInfo.getConcertName());
                vo.setGradeName(gradeInfo.getGradeName());
            }
        });
    }

    private void fillConcertAndGradeName(OrderVO vo) {
        try {
            com.ticketbooking.common.model.dto.TicketGradeDTO gradeInfo = ticketServiceClient.getGradeById(vo.getGradeId());
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
}

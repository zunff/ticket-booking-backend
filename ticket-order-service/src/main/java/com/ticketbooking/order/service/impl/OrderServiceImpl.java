package com.ticketbooking.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.constant.RedisKeyConstants;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.mq.TicketOrderMessage;
import com.ticketbooking.common.utils.RedisUtils;
import com.ticketbooking.order.config.BookingLuaScript;
import com.ticketbooking.order.model.dto.TicketInfoDTO;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.mapper.OrderMapper;
import com.ticketbooking.order.mq.OrderMessageProducer;
import com.ticketbooking.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {
    
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final long IDEMPOTENT_EXPIRE_SECONDS = 24 * 3600;
    
    private final OrderMessageProducer orderMessageProducer;
    private final RedisUtils redisUtils;
    private final BookingLuaScript bookingLuaScript;
    
    @Override
    public String createOrder(Long userId, Long concertId, Long gradeId, Integer quantity) {
        String stockKey = RedisKeyConstants.buildTicketStockKey(concertId, gradeId);
        String userTicketKey = RedisKeyConstants.buildUserTicketKey(concertId, gradeId, userId);
        
        log.info("Creating order: userId={}, concertId={}, gradeId={}, quantity={}, stockKey={}, userTicketKey={}", 
                userId, concertId, gradeId, quantity, stockKey, userTicketKey);
        
        DefaultRedisScript<Long> script = bookingLuaScript.getBookingScript();
        List<String> keys = Arrays.asList(stockKey, userTicketKey);
        Long result = redisUtils.executeLuaScript(script, keys, 
                String.valueOf(userId), String.valueOf(quantity));
        
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
                return processOrderAsync(userId, concertId, gradeId, quantity);
            default:
                throw new BusinessException(ErrorCode.SYSTEM_BUSY);
        }
    }
    
    private String processOrderAsync(Long userId, Long concertId, Long gradeId, Integer quantity) {
        TicketInfoDTO ticketInfo = getTicketInfo(concertId, gradeId);
        if (ticketInfo == null) {
            rollbackRedis(concertId, gradeId, userId, quantity);
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        
        String orderNo = generateOrderNo();
        Integer totalPrice = ticketInfo.getPrice() * quantity;
        
        String idempotentKey = RedisKeyConstants.buildOrderIdempotentKey(orderNo);
        redisUtils.setEx(idempotentKey, "PROCESSING", IDEMPOTENT_EXPIRE_SECONDS);
        
        TicketOrderMessage message = new TicketOrderMessage(
                orderNo, userId, concertId, gradeId, quantity, totalPrice);
        orderMessageProducer.sendOrderMessage(message);
        
        log.info("Order created: orderNo={}, userId={}, concertId={}, gradeId={}, quantity={}", 
                orderNo, userId, concertId, gradeId, quantity);
        
        return orderNo;
    }
    
    private void rollbackRedis(Long concertId, Long gradeId, Long userId, Integer quantity) {
        String stockKey = RedisKeyConstants.buildTicketStockKey(concertId, gradeId);
        String userTicketKey = RedisKeyConstants.buildUserTicketKey(concertId, gradeId, userId);
        redisUtils.increment(stockKey, quantity);
        redisUtils.delete(userTicketKey);
    }
    
    @Override
    public Order findByOrderNo(String orderNo) {
        return getOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
    }
    
    @Override
    public List<Order> findByUserId(Long userId) {
        return list(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime));
    }
    
    @Override
    public TicketInfoDTO getTicketInfo(Long concertId, Long gradeId) {
        String cacheKey = RedisKeyConstants.buildConcertInfoKey(concertId) + ":" + gradeId;
        String cachedInfo = redisUtils.get(cacheKey);
        if (cachedInfo != null) {
            return parseTicketInfo(cachedInfo);
        }
        return null;
    }
    
    @Override
    public boolean checkUserBoughtTicket(Long concertId, Long gradeId, Long userId) {
        String userTicketKey = RedisKeyConstants.buildUserTicketKey(concertId, gradeId, userId);
        return redisUtils.hasKey(userTicketKey);
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
            String[] parts = cachedInfo.split(":");
            if (parts.length >= 6) {
                return TicketInfoDTO.builder()
                        .concertId(Long.parseLong(parts[0]))
                        .gradeId(Long.parseLong(parts[1]))
                        .concertName(parts[2])
                        .gradeName(parts[3])
                        .price(Integer.parseInt(parts[4]))
                        .availableStock(Integer.parseInt(parts[5]))
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to parse ticket info: {}", cachedInfo, e);
        }
        return null;
    }
}

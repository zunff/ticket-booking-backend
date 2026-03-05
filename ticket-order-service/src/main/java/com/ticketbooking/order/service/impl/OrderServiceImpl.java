package com.ticketbooking.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.utils.RedisUtils;
import com.ticketbooking.order.config.BookingLuaScript;
import com.ticketbooking.order.constant.RedisKeyConstants;
import com.ticketbooking.order.model.dto.TicketInfoDTO;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.mapper.OrderMapper;
import com.ticketbooking.order.mq.OrderMessageProducer;
import com.ticketbooking.order.mq.TicketOrderMessage;
import com.ticketbooking.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    public String createOrder(Long userId, Long ticketId, Integer quantity) {
        String stockKey = RedisKeyConstants.buildTicketStockKey(ticketId);
        String userTicketKey = RedisKeyConstants.buildUserTicketKey(ticketId, userId);
        
        DefaultRedisScript<Long> script = bookingLuaScript.getBookingScript();
        List<String> keys = Arrays.asList(stockKey, userTicketKey);
        Long result = redisUtils.executeLuaScript(script, keys, 
                String.valueOf(userId), String.valueOf(quantity));
        
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
                return processOrderAsync(userId, ticketId, quantity);
            default:
                throw new BusinessException(ErrorCode.SYSTEM_BUSY);
        }
    }
    
    private String processOrderAsync(Long userId, Long ticketId, Integer quantity) {
        TicketInfoDTO ticketInfo = getTicketInfo(ticketId);
        if (ticketInfo == null) {
            rollbackRedis(ticketId, userId, quantity);
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        
        String orderNo = generateOrderNo();
        BigDecimal totalPrice = ticketInfo.getPrice().multiply(new BigDecimal(quantity));
        
        String idempotentKey = RedisKeyConstants.buildIdempotentKey(orderNo);
        redisUtils.setEx(idempotentKey, "PROCESSING", IDEMPOTENT_EXPIRE_SECONDS);
        
        TicketOrderMessage message = new TicketOrderMessage(
                orderNo, userId, ticketId, quantity, totalPrice);
        orderMessageProducer.sendOrderMessage(message);
        
        log.info("Order created: orderNo={}, userId={}, ticketId={}, quantity={}", 
                orderNo, userId, ticketId, quantity);
        
        return orderNo;
    }
    
    private void rollbackRedis(Long ticketId, Long userId, Integer quantity) {
        String stockKey = RedisKeyConstants.buildTicketStockKey(ticketId);
        String userTicketKey = RedisKeyConstants.buildUserTicketKey(ticketId, userId);
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
    public TicketInfoDTO getTicketInfo(Long ticketId) {
        String cacheKey = "ticket:info:" + ticketId;
        String cachedInfo = redisUtils.get(cacheKey);
        if (cachedInfo != null) {
            return parseTicketInfo(cachedInfo);
        }
        return null;
    }
    
    @Override
    public boolean checkUserBoughtTicket(Long ticketId, Long userId) {
        String userTicketKey = RedisKeyConstants.buildUserTicketKey(ticketId, userId);
        return redisUtils.hasKey(userTicketKey);
    }
    
    @Override
    public Order createOrderFromStock(String orderNo, Long userId, Long ticketId, 
                                      Integer quantity, BigDecimal totalPrice, String status) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTicketId(ticketId);
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
        String[] parts = cachedInfo.split(":");
        if (parts.length >= 4) {
            return TicketInfoDTO.builder()
                    .id(Long.parseLong(parts[0]))
                    .name(parts[1])
                    .price(new BigDecimal(parts[2]))
                    .availableStock(Integer.parseInt(parts[3]))
                    .build();
        }
        return null;
    }
}

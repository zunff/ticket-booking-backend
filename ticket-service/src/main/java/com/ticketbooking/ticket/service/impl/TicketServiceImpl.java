package com.ticketbooking.ticket.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.OrderStatus;
import com.ticketbooking.common.enums.TicketStatus;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.utils.RedisUtils;
import com.ticketbooking.ticket.constant.RedisKeyConstants;
import com.ticketbooking.ticket.entity.Order;
import com.ticketbooking.ticket.entity.Ticket;
import com.ticketbooking.ticket.lua.TicketBookingLuaScript;
import com.ticketbooking.ticket.mapper.OrderMapper;
import com.ticketbooking.ticket.mapper.TicketMapper;
import com.ticketbooking.ticket.mq.MessageProducer;
import com.ticketbooking.ticket.mq.TicketOrderMessage;
import com.ticketbooking.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl extends ServiceImpl<TicketMapper, Ticket> implements TicketService {
    
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String NULL_STOCK_PLACEHOLDER = "NULL";
    private static final long CACHE_NULL_EXPIRE_SECONDS = 60;
    
    private final ReentrantLock stockInitLock = new ReentrantLock();
    
    private final RedisUtils redisUtils;
    private final OrderMapper orderMapper;
    private final MessageProducer messageProducer;
    private final TicketBookingLuaScript luaScript;
    
    @Override
    public Ticket createTicket(Ticket ticket) {
        ticket.setAvailableStock(ticket.getTotalStock());
        ticket.setStatus(TicketStatus.AVAILABLE.getCode());
        save(ticket);
        
        String stockKey = RedisKeyConstants.buildTicketStockKey(ticket.getId());
        redisUtils.set(stockKey, String.valueOf(ticket.getTotalStock()));
        
        return ticket;
    }
    
    @Override
    public List<Ticket> getAllTickets() {
        return list();
    }
    
    @Override
    public List<Ticket> getAvailableTickets() {
        return list(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getStatus, TicketStatus.AVAILABLE.getCode())
                .gt(Ticket::getAvailableStock, 0));
    }
    
    @Override
    public Ticket getTicketById(Long id) {
        return getById(id);
    }
    
    @Override
    @SentinelResource(value = "bookTicket", blockHandler = "handleBookTicketBlock")
    public String bookTicket(Long ticketId, Long userId, Integer quantity) {
        String stockKey = RedisKeyConstants.buildTicketStockKey(ticketId);
        String userTicketKey = RedisKeyConstants.buildUserTicketKey(ticketId, userId);
        
        initStockWithDoubleCheck(ticketId, stockKey);
        
        DefaultRedisScript<Long> script = luaScript.getBookingScript();
        List<String> keys = Arrays.asList(stockKey, userTicketKey);
        Long result = redisUtils.executeLuaScript(script, keys, String.valueOf(userId), String.valueOf(quantity));
        
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
                return processOrderAsync(ticketId, userId, quantity);
            default:
                throw new BusinessException(ErrorCode.SYSTEM_BUSY);
        }
    }
    
    private void initStockWithDoubleCheck(Long ticketId, String stockKey) {
        String stock = redisUtils.get(stockKey);
        if (stock != null) {
            if (NULL_STOCK_PLACEHOLDER.equals(stock)) {
                throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
            }
            return;
        }
        
        stockInitLock.lock();
        try {
            stock = redisUtils.get(stockKey);
            if (stock != null) {
                if (NULL_STOCK_PLACEHOLDER.equals(stock)) {
                    throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
                }
                return;
            }
            
            Ticket ticket = getById(ticketId);
            if (ticket == null) {
                redisUtils.setEx(stockKey, NULL_STOCK_PLACEHOLDER, CACHE_NULL_EXPIRE_SECONDS);
                throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
            }
            
            redisUtils.set(stockKey, String.valueOf(ticket.getAvailableStock()));
        } finally {
            stockInitLock.unlock();
        }
    }
    
    private String processOrderAsync(Long ticketId, Long userId, Integer quantity) {
        Ticket ticket = getById(ticketId);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        
        String orderNo = generateOrderNo();
        BigDecimal totalPrice = ticket.getPrice().multiply(new BigDecimal(quantity));
        
        TicketOrderMessage message = new TicketOrderMessage(orderNo, userId, ticketId, quantity, totalPrice);
        messageProducer.sendOrderMessage(message);
        
        return orderNo;
    }
    
    @Override
    public Order getOrderByOrderNo(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
    }
    
    @Override
    public List<Order> getOrdersByUserId(Long userId) {
        return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime));
    }
    
    private String generateOrderNo() {
        return LocalDateTime.now().format(ORDER_NO_FORMATTER) 
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

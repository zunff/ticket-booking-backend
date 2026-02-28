package com.ticketbooking.ticket.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.enums.OrderStatus;
import com.ticketbooking.ticket.config.RabbitMQConfig;
import com.ticketbooking.ticket.constant.RedisKeyConstants;
import com.ticketbooking.ticket.entity.Order;
import com.ticketbooking.ticket.mapper.OrderMapper;
import com.ticketbooking.ticket.mapper.TicketMapper;
import com.ticketbooking.common.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {
    
    private final OrderMapper orderMapper;
    private final TicketMapper ticketMapper;
    private final RedisUtils redisUtils;
    
    @Qualifier("rabbitmqObjectMapper")
    private final ObjectMapper objectMapper;
    
    @RabbitListener(queues = RabbitMQConfig.TICKET_ORDER_QUEUE)
    @Transactional
    public void processOrder(Message message) {
        try {
            TicketOrderMessage orderMessage = objectMapper.readValue(message.getBody(), TicketOrderMessage.class);
            processOrderInternal(orderMessage);
        } catch (Exception e) {
            log.error("Error processing order message", e);
            throw new RuntimeException(e);
        }
    }
    
    private void processOrderInternal(TicketOrderMessage message) {
        if (orderMapper.findByOrderNo(message.getOrderNo()) != null) {
            return;
        }
        
        int updated = ticketMapper.decrementStock(message.getTicketId(), message.getQuantity());
        
        if (updated == 0) {
            rollbackRedis(message.getTicketId(), message.getUserId());
            return;
        }
        
        Order order = new Order();
        order.setOrderNo(message.getOrderNo());
        order.setTicketId(message.getTicketId());
        order.setUserId(message.getUserId());
        order.setQuantity(message.getQuantity());
        order.setTotalPrice(message.getTotalPrice());
        order.setStatus(OrderStatus.PAID.getCode());
        orderMapper.insert(order);
    }
    
    private void rollbackRedis(Long ticketId, Long userId) {
        redisUtils.increment(RedisKeyConstants.buildTicketStockKey(ticketId));
        redisUtils.delete(RedisKeyConstants.buildUserTicketKey(ticketId, userId));
    }
}

package com.ticketbooking.ticket.mq;

import com.ticketbooking.common.enums.OrderStatus;
import com.ticketbooking.ticket.config.KafkaConfig;
import com.ticketbooking.ticket.constant.RedisKeyConstants;
import com.ticketbooking.ticket.entity.Order;
import com.ticketbooking.ticket.mapper.OrderMapper;
import com.ticketbooking.ticket.mapper.TicketMapper;
import com.ticketbooking.common.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {
    
    private final OrderMapper orderMapper;
    private final TicketMapper ticketMapper;
    private final RedisUtils redisUtils;
    
    @KafkaListener(topics = KafkaConfig.TICKET_ORDER_TOPIC, groupId = "ticket-order-group")
    @Transactional
    public void processOrder(@Payload TicketOrderMessage message,
                             @Header(KafkaHeaders.RECEIVED_KEY) String key,
                             Acknowledgment acknowledgment) {
        try {
            processOrderInternal(message);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing order message: {}", message.getOrderNo(), e);
            acknowledgment.nack(Duration.ofSeconds(1));
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

package com.ticketbooking.ticket.mq;

import com.ticketbooking.common.enums.OrderStatus;
import com.ticketbooking.ticket.config.KafkaTopicConfig;
import com.ticketbooking.ticket.constant.RedisKeyConstants;
import com.ticketbooking.ticket.mapper.TicketMapper;
import com.ticketbooking.ticket.service.OrderService;
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

    private final OrderService orderService;
    private final TicketMapper ticketMapper;
    private final RedisUtils redisUtils;
    
    @KafkaListener(topics = KafkaTopicConfig.TICKET_ORDER_TOPIC, groupId = "ticket-order-group", concurrency = "5")
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
        if (orderService.findByOrderNo(message.getOrderNo()) != null) {
            return;
        }

        int updated = ticketMapper.decrementStock(message.getTicketId(), message.getQuantity());

        if (updated == 0) {
            orderService.createOrder(
                    message.getOrderNo(),
                    message.getUserId(),
                    message.getTicketId(),
                    message.getQuantity(),
                    message.getTotalPrice(),
                    OrderStatus.FAILED.getCode()
            );
            rollbackRedis(message.getTicketId(), message.getUserId());
            return;
        }

        orderService.createOrder(
                message.getOrderNo(),
                message.getUserId(),
                message.getTicketId(),
                message.getQuantity(),
                message.getTotalPrice(),
                OrderStatus.PAID.getCode()
        );
    }
    
    private void rollbackRedis(Long ticketId, Long userId) {
        redisUtils.increment(RedisKeyConstants.buildTicketStockKey(ticketId));
        redisUtils.delete(RedisKeyConstants.buildUserTicketKey(ticketId, userId));
    }
}

package com.ticketbooking.stock.mq;

import com.ticketbooking.common.constant.RedisKeyConstants;
import com.ticketbooking.common.enums.OrderStatus;
import com.ticketbooking.common.mq.TicketOrderMessage;
import com.ticketbooking.common.utils.RedisUtils;
import com.ticketbooking.stock.client.OrderServiceClient;
import com.ticketbooking.stock.config.KafkaTopicConfig;
import com.ticketbooking.stock.model.dto.OrderDTO;
import com.ticketbooking.stock.model.qo.CreateOrderQO;
import com.ticketbooking.stock.service.StockService;
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
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {

    private final OrderServiceClient orderServiceClient;
    private final StockService stockService;
    private final RedisUtils redisUtils;
    
    @KafkaListener(topics = KafkaTopicConfig.TICKET_ORDER_TOPIC, groupId = "stock-group", concurrency = "5")
    @Transactional
    public void processOrder(@Payload TicketOrderMessage message,
                             @Header(KafkaHeaders.RECEIVED_KEY) String key,
                             Acknowledgment acknowledgment) {
        String orderNo = message.getOrderNo();
        log.info("Processing order message: {}", orderNo);
        
        try {
            String idempotentKey = RedisKeyConstants.buildConsumeIdempotentKey(orderNo);
            Boolean setSuccess = redisUtils.setIfAbsent(idempotentKey, "1", 24, TimeUnit.HOURS);
            if (setSuccess == null || !setSuccess) {
                log.info("Order already processed (idempotent): {}", orderNo);
                acknowledgment.acknowledge();
                return;
            }
            
            OrderDTO existingOrder = orderServiceClient.findByOrderNo(orderNo);
            if (existingOrder != null) {
                log.info("Order already exists in DB: {}", orderNo);
                acknowledgment.acknowledge();
                return;
            }
            
            int updated = stockService.decrementStock(
                    message.getConcertId(), 
                    message.getGradeId(), 
                    message.getQuantity(), 
                    orderNo);
            
            if (updated == 0) {
                log.warn("Stock decrement failed, creating failed order: {}", orderNo);
                CreateOrderQO failedQO = new CreateOrderQO(
                        orderNo,
                        message.getUserId(),
                        message.getConcertId(),
                        message.getGradeId(),
                        message.getQuantity(),
                        message.getTotalPrice(),
                        OrderStatus.FAILED.getCode()
                );
                orderServiceClient.createOrder(failedQO);
                rollbackRedis(message.getConcertId(), message.getGradeId(), message.getUserId(), message.getQuantity());
                redisUtils.delete(idempotentKey);
            } else {
                log.info("Stock decremented successfully, creating paid order: {}", orderNo);
                CreateOrderQO paidQO = new CreateOrderQO(
                        orderNo,
                        message.getUserId(),
                        message.getConcertId(),
                        message.getGradeId(),
                        message.getQuantity(),
                        message.getTotalPrice(),
                        OrderStatus.PAID.getCode()
                );
                orderServiceClient.createOrder(paidQO);
            }
            
            acknowledgment.acknowledge();
            log.info("Order processed successfully: {}", orderNo);
            
        } catch (Exception e) {
            log.error("Error processing order message: {}", orderNo, e);
            redisUtils.delete(RedisKeyConstants.buildConsumeIdempotentKey(orderNo));
            acknowledgment.nack(Duration.ofSeconds(1));
        }
    }
    
    private void rollbackRedis(Long concertId, Long gradeId, Long userId, Integer quantity) {
        String stockKey = RedisKeyConstants.buildTicketStockKey(concertId, gradeId);
        String userTicketKey = RedisKeyConstants.buildUserTicketKey(concertId, gradeId, userId);
        redisUtils.increment(stockKey, quantity);
        redisUtils.delete(userTicketKey);
        log.info("Redis rolled back: concertId={}, gradeId={}, userId={}, quantity={}", 
                concertId, gradeId, userId, quantity);
    }
}

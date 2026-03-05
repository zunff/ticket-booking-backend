package com.ticketbooking.order.mq;

import com.ticketbooking.order.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageProducer {
    
    private final KafkaTemplate<String, TicketOrderMessage> kafkaTemplate;
    
    public void sendOrderMessage(TicketOrderMessage message) {
        CompletableFuture<SendResult<String, TicketOrderMessage>> future = 
                kafkaTemplate.send(KafkaTopicConfig.TICKET_ORDER_TOPIC, message.getOrderNo(), message);
        
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send order message: {}, error: {}", 
                        message.getOrderNo(), ex.getMessage());
            } else {
                log.info("Successfully sent order message: {}, partition: {}, offset: {}", 
                        message.getOrderNo(), 
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
    
    public void sendOrderMessageSync(TicketOrderMessage message) throws Exception {
        SendResult<String, TicketOrderMessage> result = 
                kafkaTemplate.send(KafkaTopicConfig.TICKET_ORDER_TOPIC, message.getOrderNo(), message).get();
        log.info("Sync sent order message: {}, partition: {}, offset: {}", 
                message.getOrderNo(), 
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
    }
}

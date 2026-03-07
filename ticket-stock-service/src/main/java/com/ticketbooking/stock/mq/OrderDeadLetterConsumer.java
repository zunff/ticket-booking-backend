package com.ticketbooking.stock.mq;

import com.ticketbooking.common.mq.TicketOrderMessage;
import com.ticketbooking.stock.config.KafkaTopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class OrderDeadLetterConsumer {

    @KafkaListener(topics = KafkaTopicConfig.TICKET_ORDER_DLT, groupId = "stock-dlt-group", concurrency = "3")
    public void handleDeadLetter(
            @Payload(required = false) TicketOrderMessage message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String originalTopic,
            @Header(KafkaHeaders.DLT_ORIGINAL_PARTITION) Integer originalPartition,
            @Header(KafkaHeaders.DLT_ORIGINAL_OFFSET) Long originalOffset,
            Headers headers,
            Acknowledgment acknowledgment) {
        
        try {
            String exceptionMessage = getExceptionMessage(headers);
            
            log.error("Dead letter received: originalTopic={}, partition={}, offset={}, exception={}, message={}",
                    originalTopic, originalPartition, originalOffset, exceptionMessage, message);
            
            acknowledgment.acknowledge();
            log.info("Dead letter processed: orderNo={}", 
                    message != null ? message.getOrderNo() : "null");
            
        } catch (Exception e) {
            log.error("Failed to process dead letter: offset={}", originalOffset, e);
        }
    }
    
    private String getExceptionMessage(Headers headers) {
        org.apache.kafka.common.header.Header exceptionHeader = 
                headers.lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        if (exceptionHeader != null) {
            return new String(exceptionHeader.value(), StandardCharsets.UTF_8);
        }
        return "Unknown exception";
    }
}

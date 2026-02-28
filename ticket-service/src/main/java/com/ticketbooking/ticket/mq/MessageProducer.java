package com.ticketbooking.ticket.mq;

import com.ticketbooking.ticket.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageProducer {
    
    private final KafkaTemplate<String, TicketOrderMessage> kafkaTemplate;
    
    public void sendOrderMessage(TicketOrderMessage ticketOrderMessage) {
        try {
            kafkaTemplate.send(KafkaConfig.TICKET_ORDER_TOPIC, ticketOrderMessage.getOrderNo(), ticketOrderMessage)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send order message: {}", ticketOrderMessage.getOrderNo(), ex);
                        } else {
                            log.debug("Sent order message: {}", ticketOrderMessage.getOrderNo());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to send order message: {}", ticketOrderMessage.getOrderNo(), e);
        }
    }
}

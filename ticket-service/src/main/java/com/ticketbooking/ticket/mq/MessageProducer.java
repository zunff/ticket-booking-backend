package com.ticketbooking.ticket.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.ticket.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageProducer {
    
    private final RabbitTemplate rabbitTemplate;
    
    @Qualifier("rabbitmqObjectMapper")
    private final ObjectMapper objectMapper;
    
    public void sendOrderMessage(TicketOrderMessage ticketOrderMessage) {
        try {
            String json = objectMapper.writeValueAsString(ticketOrderMessage);
            
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding("UTF-8");
            props.setHeader("__TypeId__", TicketOrderMessage.class.getName());
            
            Message message = new Message(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), props);
            
            rabbitTemplate.send(
                    RabbitMQConfig.TICKET_ORDER_EXCHANGE,
                    RabbitMQConfig.TICKET_ORDER_ROUTING_KEY,
                    message
            );
        } catch (Exception e) {
            log.error("Failed to send order message: {}", ticketOrderMessage.getOrderNo(), e);
        }
    }
}

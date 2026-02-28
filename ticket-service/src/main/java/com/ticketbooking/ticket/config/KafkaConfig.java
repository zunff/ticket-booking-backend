package com.ticketbooking.ticket.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
    
    public static final String TICKET_ORDER_TOPIC = "ticket-order-topic";
    public static final String TICKET_ORDER_DLQ = "ticket-order-dlq";
    
    @Bean
    public NewTopic ticketOrderTopic() {
        return TopicBuilder.name(TICKET_ORDER_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
    
    @Bean
    public NewTopic ticketOrderDlq() {
        return TopicBuilder.name(TICKET_ORDER_DLQ)
                .partitions(1)
                .replicas(1)
                .build();
    }
}

package com.ticketbooking.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String TICKET_ORDER_TOPIC = "ticket-order-topic";
    public static final String TICKET_ORDER_DLT = TICKET_ORDER_TOPIC + ".DLT";
    
    @Bean
    public NewTopic ticketOrderTopic() {
        return TopicBuilder.name(TICKET_ORDER_TOPIC)
                .partitions(10)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ticketOrderDlt() {
        return TopicBuilder.name(TICKET_ORDER_DLT)
                .partitions(10)
                .replicas(1)
                .build();
    }
}

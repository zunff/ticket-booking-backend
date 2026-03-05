package com.ticketbooking.ticket.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String DLT = ".DLT";
    
    public static final String TICKET_ORDER_TOPIC = "ticket-order-topic";
    public static final String TICKET_ORDER_DLT = TICKET_ORDER_TOPIC + DLT;
    
    @Bean
    public NewTopic ticketOrderTopic() {
        return TopicBuilder.name(TICKET_ORDER_TOPIC)
                .partitions(5)
                .replicas(1)
                .build();
    }

    /**
     * 死信队列：分区数和原Topic一致，副本数一致
     * 非强制 推荐一致
     */
    @Bean
    public NewTopic ticketOrderDlq() {
        return TopicBuilder.name(TICKET_ORDER_DLT)
                .partitions(5)
                .replicas(1)
                .build();
    }
}

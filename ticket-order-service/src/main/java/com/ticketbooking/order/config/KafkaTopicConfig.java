package com.ticketbooking.order.config;

import com.ticketbooking.common.constant.KafkaTopicConstants;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka Topic 创建配置
 * <p>
 * order 服务是 TICKET_ORDER_TOPIC 的生产者,由本类单一声明 topic 的物理参数
 * (分区数、副本数)。消费者方(stock 服务)只引用 {@link KafkaTopicConstants} 中的常量,
 * 不重复声明,避免配置漂移。
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic ticketOrderTopic() {
        return TopicBuilder.name(KafkaTopicConstants.TICKET_ORDER_TOPIC)
                .partitions(10)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ticketOrderDlt() {
        return TopicBuilder.name(KafkaTopicConstants.TICKET_ORDER_DLT)
                .partitions(10)
                .replicas(1)
                .build();
    }
}

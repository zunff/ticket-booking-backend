package com.ticketbooking.ticket.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaDeadLetterConfig {

    /**
     * 配置死信队列错误处理器
     * 核心逻辑：先重试3次，每次间隔1s，仍失败则转发到死信队列
     */
    @Bean
    public CommonErrorHandler deadLetterErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // 1. 定义死信队列发布恢复器：将失败消息转发到死信队列
        // 默认死信队列命名规则：原Topic名 + ".DLT"
        // 比如原Topic是 "ticket-order-topic"，死信队列就是 "ticket-order-topic.DLT"
        DeadLetterPublishingRecoverer deadLetterRecoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                // 可选：自定义死信队列的命名规则，这里用默认的即可
                (r, e) -> new TopicPartition(r.topic() + ".DLT", r.partition())
        );

        // 2. 定义重试策略：指数退避
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1000L);       // 初始重试间隔：1秒
        backOff.setMultiplier(2.0);              // 间隔倍增因子：每次×2
        backOff.setMaxAttempts(5);               // 最大重试次数：5次

        // 3. 创建默认错误处理器：先重试，重试失败转发死信队列
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(deadLetterRecoverer, backOff);

        // 4. 可选：配置哪些异常不重试，直接转发死信队列（比如消息格式错误，重试也没用）
        // errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        // 可选：配置哪些异常需要重试（默认除了不可重试的都重试）
        // errorHandler.addRetryableExceptions(RuntimeException.class);

        return errorHandler;
    }
}
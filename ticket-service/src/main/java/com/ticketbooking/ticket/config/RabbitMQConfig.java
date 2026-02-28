package com.ticketbooking.ticket.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {
    
    public static final String TICKET_ORDER_QUEUE = "ticket.order.queue";
    public static final String TICKET_ORDER_DLQ = "ticket.order.dlq";
    public static final String TICKET_ORDER_EXCHANGE = "ticket.order.exchange";
    public static final String TICKET_ORDER_ROUTING_KEY = "ticket.order.routing.key";
    
    @Bean
    public ObjectMapper rabbitmqObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
    
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper rabbitmqObjectMapper) {
        return new Jackson2JsonMessageConverter(rabbitmqObjectMapper);
    }
    
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
    
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setConcurrentConsumers(5);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(5);
        return factory;
    }
    
    @Bean
    public DirectExchange ticketOrderExchange() {
        return new DirectExchange(TICKET_ORDER_EXCHANGE, true, false);
    }
    
    @Bean
    public Queue ticketOrderQueue() {
        return QueueBuilder.durable(TICKET_ORDER_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", TICKET_ORDER_DLQ)
                .build();
    }
    
    @Bean
    public Queue ticketOrderDeadLetterQueue() {
        return new Queue(TICKET_ORDER_DLQ, true);
    }
    
    @Bean
    public Binding ticketOrderBinding() {
        return BindingBuilder.bind(ticketOrderQueue())
                .to(ticketOrderExchange())
                .with(TICKET_ORDER_ROUTING_KEY);
    }
}

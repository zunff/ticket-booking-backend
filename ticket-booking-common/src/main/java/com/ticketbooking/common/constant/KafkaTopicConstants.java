package com.ticketbooking.common.constant;

/**
 * Kafka Topic 常量
 * <p>
 * 跨服务共享的 topic 名称契约。topic 的实际创建(@Bean NewTopic)由生产者方
 * 单一声明,消费者方只引用本常量,不重复声明。
 */
public final class KafkaTopicConstants {

    private KafkaTopicConstants() {}

    public static final String TICKET_ORDER_TOPIC = "ticket-order-topic";
    public static final String TICKET_ORDER_DLT = TICKET_ORDER_TOPIC + ".DLT";
}

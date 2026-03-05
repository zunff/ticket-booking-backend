package com.ticketbooking.stock.constant;

public class KafkaTopicConstants {
    
    private KafkaTopicConstants() {}
    
    public static final String TICKET_ORDER_TOPIC = "ticket-order-topic";
    public static final String TICKET_ORDER_DLT = TICKET_ORDER_TOPIC + ".DLT";
}

package com.ticketbooking.stock.constant;

public class RedisKeyConstants {
    
    private RedisKeyConstants() {}
    
    public static final String TICKET_STOCK_KEY = "ticket:stock:";
    public static final String USER_TICKET_KEY = "user:ticket:";
    public static final String CONSUME_IDEMPOTENT_KEY = "stock:consume:idempotent:";
    
    public static String buildTicketStockKey(Long ticketId) {
        return TICKET_STOCK_KEY + ticketId;
    }
    
    public static String buildUserTicketKey(Long ticketId, Long userId) {
        return USER_TICKET_KEY + ticketId + ":" + userId;
    }
    
    public static String buildConsumeIdempotentKey(String orderNo) {
        return CONSUME_IDEMPOTENT_KEY + orderNo;
    }
}

package com.ticketbooking.order.constant;

public class RedisKeyConstants {
    
    private RedisKeyConstants() {}
    
    public static final String TICKET_STOCK_KEY = "ticket:stock:";
    public static final String ORDER_IDEMPOTENT_KEY = "order:idempotent:";
    public static final String ORDER_RATE_LIMIT_KEY = "order:rate:";
    public static final String USER_TICKET_KEY = "user:ticket:";
    
    public static String buildTicketStockKey(Long ticketId) {
        return TICKET_STOCK_KEY + ticketId;
    }
    
    public static String buildIdempotentKey(String orderNo) {
        return ORDER_IDEMPOTENT_KEY + orderNo;
    }
    
    public static String buildRateLimitKey(Long userId) {
        return ORDER_RATE_LIMIT_KEY + userId;
    }
    
    public static String buildUserTicketKey(Long ticketId, Long userId) {
        return USER_TICKET_KEY + ticketId + ":" + userId;
    }
}

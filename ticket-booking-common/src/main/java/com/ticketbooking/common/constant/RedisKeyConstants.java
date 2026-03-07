package com.ticketbooking.common.constant;

public final class RedisKeyConstants {
    
    private RedisKeyConstants() {}
    
    public static final String CONCERT_INFO_KEY = "concert:info:";
    public static final String TICKET_STOCK_KEY = "ticket:stock:";
    public static final String TICKET_LOCK_KEY = "ticket:lock:";
    public static final String USER_TICKET_KEY = "user:ticket:";
    public static final String ORDER_IDEMPOTENT_KEY = "order:idempotent:";
    public static final String ORDER_RATE_LIMIT_KEY = "order:rate:";
    public static final String CONSUME_IDEMPOTENT_KEY = "consume:idempotent:";
    
    public static String buildConcertInfoKey(Long concertId) {
        return CONCERT_INFO_KEY + concertId;
    }
    
    public static String buildTicketStockKey(Long concertId, Long gradeId) {
        return TICKET_STOCK_KEY + concertId + ":" + gradeId;
    }
    
    public static String buildTicketLockKey(Long concertId, Long gradeId) {
        return TICKET_LOCK_KEY + concertId + ":" + gradeId;
    }
    
    public static String buildUserTicketKey(Long concertId, Long gradeId, Long userId) {
        return USER_TICKET_KEY + concertId + ":" + gradeId + ":" + userId;
    }
    
    public static String buildOrderIdempotentKey(String orderNo) {
        return ORDER_IDEMPOTENT_KEY + orderNo;
    }
    
    public static String buildOrderRateLimitKey(Long userId) {
        return ORDER_RATE_LIMIT_KEY + userId;
    }
    
    public static String buildConsumeIdempotentKey(String orderNo) {
        return CONSUME_IDEMPOTENT_KEY + orderNo;
    }
}

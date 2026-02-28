package com.ticketbooking.ticket.constant;

public class RedisKeyConstants {
    
    private RedisKeyConstants() {}
    
    public static final String TICKET_STOCK_KEY = "ticket:stock:";
    public static final String TICKET_LOCK_KEY = "ticket:lock:";
    public static final String USER_TICKET_KEY = "user:ticket:";

    public static String buildTicketStockKey(Long ticketId) {
        return TICKET_STOCK_KEY + ticketId;
    }
    
    public static String buildTicketLockKey(Long ticketId) {
        return TICKET_LOCK_KEY + ticketId;
    }
    
    public static String buildUserTicketKey(Long ticketId, Long userId) {
        return USER_TICKET_KEY + ticketId + ":" + userId;
    }
}

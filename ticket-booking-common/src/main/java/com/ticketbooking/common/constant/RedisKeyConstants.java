package com.ticketbooking.common.constant;

public final class RedisKeyConstants {

    private RedisKeyConstants() {}

    public static final String CONCERT_INFO_KEY = "concert:info:";
    public static final String TICKET_STOCK_KEY = "ticket:stock:";
    public static final String CONSUME_IDEMPOTENT_KEY = "consume:idempotent:";
    public static final String TICKET_LOCK_KEY = "lock:ticket:grade:info:";

    /**
     * 用户在演唱会的购买数量（演唱会级别限购）
     * Key: user:concert:purchase:{concertId}:{userId}
     * Value: 购买数量
     */
    public static final String USER_CONCERT_PURCHASE_KEY = "user:concert:purchase:";

    /**
     * 演唱会限购数量
     * Key: concert:limit:{concertId}
     * Value: 限购数量
     */
    public static final String CONCERT_LIMIT_KEY = "concert:limit:";

    public static String buildConcertInfoKey(Long concertId) {
        return CONCERT_INFO_KEY + concertId;
    }

    public static String buildTicketStockKey(Long concertId, Long gradeId) {
        return TICKET_STOCK_KEY + concertId + ":" + gradeId;
    }

    public static String buildTicketLockKey(Long concertId, Long gradeId) {
        return TICKET_LOCK_KEY + concertId + ":" + gradeId;
    }

    /**
     * 用户在演唱会的购买数量 Key
     */
    public static String buildUserConcertPurchaseKey(Long concertId, Long userId) {
        return USER_CONCERT_PURCHASE_KEY + concertId + ":" + userId;
    }

    /**
     * 演唱会限购数量 Key
     */
    public static String buildConcertLimitKey(Long concertId) {
        return CONCERT_LIMIT_KEY + concertId;
    }


    public static String buildConsumeIdempotentKey(String orderNo) {
        return CONSUME_IDEMPOTENT_KEY + orderNo;
    }
}

package com.ticketbooking.common.constant;

public final class RedisKeyConstants {

    private RedisKeyConstants() {}

    // ==================== 用户相关 ====================

    /**
     * 用户信息缓存
     * Key: user:info:{userId}
     * Value: JSON 序列化的用户信息
     */
    public static final String USER_INFO_KEY = "user:info:";

    // ==================== 演唱会相关 ====================

    public static final String CONCERT_INFO_KEY = "concert:info:";
    public static final String TICKET_STOCK_KEY = "ticket:stock:";
    public static final String CONSUME_IDEMPOTENT_KEY = "consume:idempotent:";
    public static final String TICKET_LOCK_KEY = "lock:ticket:grade:info:";

    /**
     * 票价档位缓存
     * Key: ticket:grade:{concertId}
     * Value: JSON 序列化的票价档位列表
     */
    public static final String TICKET_GRADE_KEY = "ticket:grade:";

    public static final String EMPTY_KEY = "_empty";

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

    /**
     * 演唱会库存 Hash Key (一个演唱会一个 Hash，field 为 gradeId)
     */
    public static String buildTicketStockHashKey(Long concertId) {
        return TICKET_STOCK_KEY + concertId;
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

    // ==================== Kafka 降级相关 ====================

    /**
     * Kafka 降级消息 Redis Stream Key
     * 用于存储发送失败的 Kafka 消息，后续重试
     */
    public static final String KAFKA_FALLBACK_STREAM_KEY = "kafka:fallback:order-stream";

    // ==================== 分布式锁 ====================

    /**
     * 支付下单分布式锁前缀
     * Key: payment:prepay:lock:{outTradeNo}
     */
    public static final String PAYMENT_PREPAY_LOCK_KEY = "payment:prepay:lock:";

    public static String buildConsumeIdempotentKey(String orderNo) {
        return CONSUME_IDEMPOTENT_KEY + orderNo;
    }

    /**
     * 用户信息缓存 Key
     */
    public static String buildUserInfoKey(Long userId) {
        return USER_INFO_KEY + userId;
    }

    /**
     * 票价档位缓存 Key
     */
    public static String buildTicketGradeKey(Long concertId) {
        return TICKET_GRADE_KEY + concertId;
    }

    /**
     * 支付下单分布式锁 Key
     */
    public static String buildPaymentPrepayLockKey(String outTradeNo) {
        return PAYMENT_PREPAY_LOCK_KEY + outTradeNo;
    }
}

package com.ticketbooking.common.constant;

/**
 * Redis Key 过期时间常量
 * 统一管理所有缓存过期时间，便于维护和调整
 */
public final class RedisExpireConstants {

    private RedisExpireConstants() {}

    // ==================== 预热缓存 ====================

    /**
     * 预热缓存过期时间：24 小时
     * 用于：演唱会信息、限购数量、库存 Hash
     */
    public static final long PREHEAT_CACHE_HOURS = 24;


    // ==================== 用户购买记录 ====================

    /**
     * 用户购买数量 Key 过期时间：24 小时
     * 用于：user:concert:purchase:{concertId}:{userId}
     * 说明：记录用户在演唱会的购买数量，24小时内有效
     */
    public static final long USER_PURCHASE_SECONDS = 86400L;

    // ==================== 幂等性 ====================

    /**
     * 消费幂等 Key 过期时间：24 小时
     * 用于：consume:idempotent:{orderNo}
     * 说明：防止 Kafka 消息重复消费
     */
    public static final long CONSUME_IDEMPOTENT_HOURS = 24;

    // ==================== 兜底缓存 ====================

    /**
     * 兜底缓存过期时间：1.5 小时
     * 用于：预热数据过期后的兜底同步
     * 说明：比预热缓存短，确保兜底数据不会长期存在
     */
    public static final long FALLBACK_CACHE_SECONDS = 5400L; // 3600 + 1800

    // ==================== 缓存穿透防护 ====================

    /**
     * 空值缓存过期时间：5 分钟
     * 用于：防止缓存穿透，当查询结果为空时缓存空值
     * 说明：较短的过期时间，避免占用过多内存
     */
    public static final long NULL_CACHE_SECONDS = 300L;



    /**
     * 用户信息 Redis 过期时间（秒）
     */
    public static final long USER_INFO_EXPIRE_SECONDS = 1800L; // 30 分钟

    /**
     * 票价档位 Redis 过期时间（秒）
     */
    public static final long TICKET_GRADE_EXPIRE_SECONDS = 3600L; // 1 小时

}

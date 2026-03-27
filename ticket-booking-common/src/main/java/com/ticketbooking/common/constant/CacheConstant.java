package com.ticketbooking.common.constant;

/**
 * 缓存常量
 * 定义各缓存的名称、key 前缀和过期时间
 */
public final class CacheConstant {

    private CacheConstant() {}

    // ==================== 缓存名称 ====================

    /**
     * 用户信息缓存
     */
    public static final String CACHE_USER = "user";

    /**
     * 演唱会信息缓存
     */
    public static final String CACHE_CONCERT = "concert";

    /**
     * 票价档位缓存
     */
    public static final String CACHE_TICKET_GRADE = "ticketGrade";

    // ==================== Caffeine 配置 ====================

    /**
     * 用户缓存最大容量
     */
    public static final int USER_CACHE_MAX_SIZE = 1000;

    /**
     * 用户缓存过期时间（分钟）
     */
    public static final int USER_CACHE_EXPIRE_MINUTES = 5;

    /**
     * 演唱会缓存最大容量
     */
    public static final int CONCERT_CACHE_MAX_SIZE = 100;

    /**
     * 演唱会缓存过期时间（分钟）
     */
    public static final int CONCERT_CACHE_EXPIRE_MINUTES = 10;

    /**
     * 票价档位缓存最大容量
     */
    public static final int TICKET_GRADE_CACHE_MAX_SIZE = 500;

    /**
     * 票价档位缓存过期时间（分钟）
     */
    public static final int TICKET_GRADE_CACHE_EXPIRE_MINUTES = 10;

    // ==================== Redis 过期时间 ====================

    /**
     * 用户信息 Redis 过期时间（秒）
     */
    public static final long USER_REDIS_EXPIRE_SECONDS = 1800L; // 30 分钟

    /**
     * 票价档位 Redis 过期时间（秒）
     */
    public static final long TICKET_GRADE_REDIS_EXPIRE_SECONDS = 3600L; // 1 小时

    // ==================== Pub/Sub 频道 ====================

    /**
     * 缓存失效消息频道
     */
    public static final String CACHE_EVICTION_CHANNEL = "cache:eviction";
}

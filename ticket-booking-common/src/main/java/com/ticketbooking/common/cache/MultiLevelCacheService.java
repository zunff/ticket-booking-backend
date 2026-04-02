package com.ticketbooking.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.ticketbooking.common.constant.CacheConstant;
import com.ticketbooking.common.utils.RedisUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 多级缓存服务
 * L1: Caffeine (本地缓存) - 可选
 * L2: Redis (分布式缓存)
 *
 * 通过配置类创建 Bean，不使用 @Service 自动扫描
 */
@Slf4j
public class MultiLevelCacheService {

    private final RedisUtils redisUtils;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 本地缓存（可选注入，由各服务按需配置）
     */
    private final Cache<String, Object> localCache;

    /**
     * 缓存名称（用于日志和消息）
     */
    private final String cacheName;

    /**
     * 当前实例 ID
     */
    @Getter
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    /**
     * 构造函数（无本地缓存，只用 Redis）
     */
    public MultiLevelCacheService(RedisUtils redisUtils,
                                   StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper) {
        this.redisUtils = redisUtils;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.localCache = null;
        this.cacheName = null;
    }

    /**
     * 构造函数（带本地缓存）
     */
    public MultiLevelCacheService(RedisUtils redisUtils,
                                   StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper,
                                   Cache<String, Object> localCache,
                                   String cacheName) {
        this.redisUtils = redisUtils;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.localCache = localCache;
        this.cacheName = cacheName;
    }

    /**
     * 获取缓存（支持多级查询）
     */
    public <T> T get(String key, Class<T> type, String redisKey,
                     long redisTtl, Supplier<T> loader) {
        // 1. 查询 L1 (Caffeine)
        if (localCache != null) {
            T value = getFromL1(key, type);
            if (value != null) {
                log.debug("[多级缓存] L1 命中: key={}", key);
                return value;
            }
        }

        // 2. 查询 L2 (Redis)
        T value = getFromL2(redisKey, type);
        if (value != null) {
            log.debug("[缓存] L2 命中: key={}", key);
            // 回填 L1
            if (localCache != null) {
                putToL1(key, value);
            }
            return value;
        }

        // 3. 加载数据
        if (loader != null) {
            value = loader.get();
            if (value != null) {
                put(key, redisKey, value, redisTtl);
            }
        }

        return value;
    }

    /**
     * 写入缓存
     */
    public <T> void put(String key, String redisKey, T value, long redisTtl) {
        // 写入 L1
        if (localCache != null) {
            putToL1(key, value);
        }
        // 写入 L2
        putToL2(redisKey, value, redisTtl);
    }

    /**
     * 清除缓存
     */
    public void evict(String key, String redisKey) {
        // 清除本地 L1
        if (localCache != null) {
            localCache.invalidate(key);
            log.debug("[多级缓存] 清除 L1: key={}", key);
        }
        // 清除 L2
        evictL2(redisKey);
        // 发布失效消息通知其他实例
        if (localCache != null && cacheName != null) {
            publishEviction(key);
        }
    }

    /**
     * 处理远程失效消息（由 CacheEvictionListener 调用）
     */
    public void handleRemoteEviction(String key) {
        if (localCache != null) {
            localCache.invalidate(key);
            log.info("[多级缓存] 远程失效 L1: key={}", key);
        }
    }

    // ==================== L1 (Caffeine) 操作 ====================

    @SuppressWarnings("unchecked")
    private <T> T getFromL1(String key, Class<T> type) {
        if (localCache == null) {
            return null;
        }
        Object value = localCache.getIfPresent(key);
        return type.isInstance(value) ? (T) value : null;
    }

    private <T> void putToL1(String key, T value) {
        if (localCache == null) {
            return;
        }
        localCache.put(key, value);
    }

    // ==================== L2 (Redis) 操作 ====================

    private <T> T getFromL2(String key, Class<T> type) {
        String json = redisUtils.get(key);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.error("[缓存] Redis 反序列化失败: key={}", key, e);
            return null;
        }
    }

    private <T> void putToL2(String key, T value, long ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisUtils.set(key, json, ttl, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("[缓存] Redis 序列化失败: key={}", key, e);
        }
    }

    private void evictL2(String key) {
        redisUtils.delete(key);
        log.debug("[缓存] 清除 L2: key={}", key);
    }

    // ==================== Pub/Sub 操作 ====================

    private void publishEviction(String key) {
        CacheEvictionMessage message = new CacheEvictionMessage(key, instanceId, cacheName);
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(CacheConstant.CACHE_EVICTION_CHANNEL, json);
            log.debug("[多级缓存] 发布失效消息: key={}", key);
        } catch (JsonProcessingException e) {
            log.error("[多级缓存] 序列化失效消息失败", e);
        }
    }
}

package com.ticketbooking.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.constant.CacheConstant;
import com.ticketbooking.common.utils.RedisUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 多级缓存服务
 * L1: Caffeine (本地缓存)
 * L2: Redis (分布式缓存)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiLevelCacheService {

    private final CacheManager cacheManager;
    private final RedisUtils redisUtils;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 当前实例 ID
     */
    @Getter
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    /**
     * 获取缓存（支持多级查询）
     *
     * @param cacheName  缓存名称
     * @param key        缓存 key
     * @param type       返回类型
     * @param redisKey   Redis key（完整 key）
     * @param redisTtl   Redis 过期时间（秒）
     * @param loader     数据加载器
     * @param <T>        返回类型
     * @return 缓存值
     */
    public <T> T get(String cacheName, String key, Class<T> type, String redisKey,
                     long redisTtl, Supplier<T> loader) {
        // 1. 查询 L1 (Caffeine)
        T value = getFromL1(cacheName, key, type);
        if (value != null) {
            log.debug("[多级缓存] L1 命中: cacheName={}, key={}", cacheName, key);
            return value;
        }

        // 2. 查询 L2 (Redis)
        value = getFromL2(redisKey, type);
        if (value != null) {
            log.debug("[多级缓存] L2 命中: cacheName={}, key={}", cacheName, key);
            // 回填 L1
            putToL1(cacheName, key, value);
            return value;
        }

        // 3. 加载数据
        if (loader != null) {
            value = loader.get();
            if (value != null) {
                // 写入 L1 和 L2
                put(cacheName, key, redisKey, value, redisTtl);
            }
        }

        return value;
    }

    /**
     * 写入缓存（同时写入 L1 和 L2）
     */
    public <T> void put(String cacheName, String key, String redisKey, T value, long redisTtl) {
        // 写入 L1
        putToL1(cacheName, key, value);
        // 写入 L2
        putToL2(redisKey, value, redisTtl);
    }

    /**
     * 清除缓存（通过 Pub/Sub 通知所有实例）
     */
    public void evict(String cacheName, String key, String redisKey) {
        // 清除本地 L1
        evictL1(cacheName, key);
        // 清除 L2
        evictL2(redisKey);
        // 发布失效消息通知其他实例
        publishEviction(cacheName, key, false);
    }

    /**
     * Pattern 清除缓存（通过 Pub/Sub 通知所有实例）
     */
    public void evictByPattern(String cacheName, String keyPattern, String redisKeyPattern) {
        // 清除本地 L1 (pattern)
        evictL1ByPattern(cacheName, keyPattern);
        // 清除 L2 (pattern) - Redis 不支持直接 pattern 删除，需要单独处理
        // 通常由调用方自行清除 Redis
        // 发布失效消息通知其他实例
        publishEviction(cacheName, keyPattern, true);
    }

    // ==================== L1 (Caffeine) 操作 ====================

    @SuppressWarnings("unchecked")
    private <T> T getFromL1(String cacheName, String key, Class<T> type) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return null;
        }
        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper == null) {
            return null;
        }
        Object value = wrapper.get();
        return type.isInstance(value) ? (T) value : null;
    }

    private <T> void putToL1(String cacheName, String key, T value) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    private void evictL1(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
            log.debug("[多级缓存] 清除 L1: cacheName={}, key={}", cacheName, key);
        }
    }

    private void evictL1ByPattern(String cacheName, String pattern) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }

        Object nativeCache = cache.getNativeCache();
        if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache) {
            @SuppressWarnings("unchecked")
            com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache =
                    (com.github.benmanes.caffeine.cache.Cache<Object, Object>) nativeCache;

            String prefix = pattern.replace("*", "");
            caffeineCache.asMap().keySet().removeIf(k -> String.valueOf(k).startsWith(prefix));
            log.debug("[多级缓存] Pattern 清除 L1: cacheName={}, pattern={}", cacheName, pattern);
        }
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
            log.error("[多级缓存] Redis 反序列化失败: key={}", key, e);
            return null;
        }
    }

    private <T> void putToL2(String key, T value, long ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisUtils.set(key, json, ttl, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("[多级缓存] Redis 序列化失败: key={}", key, e);
        }
    }

    private void evictL2(String key) {
        redisUtils.delete(key);
        log.debug("[多级缓存] 清除 L2: key={}", key);
    }

    // ==================== Pub/Sub 操作 ====================

    private void publishEviction(String cacheName, String key, boolean patternMatch) {
        CacheEvictionMessage message = new CacheEvictionMessage(key, instanceId, cacheName, patternMatch);
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(CacheConstant.CACHE_EVICTION_CHANNEL, json);
            log.debug("[多级缓存] 发布失效消息: cacheName={}, key={}", cacheName, key);
        } catch (JsonProcessingException e) {
            log.error("[多级缓存] 序列化失效消息失败", e);
        }
    }
}

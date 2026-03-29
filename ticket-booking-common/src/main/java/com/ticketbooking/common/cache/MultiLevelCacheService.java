package com.ticketbooking.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.constant.CacheConstant;
import com.ticketbooking.common.utils.RedisUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 多级缓存服务
 * L1: Caffeine (本地缓存) - 可选
 * L2: Redis (分布式缓存)
 *
 * 如果未配置 CacheManager，则只使用 L2 (Redis)
 */
@Slf4j
@Service
public class MultiLevelCacheService {

    private final RedisUtils redisUtils;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * CacheManager 可选注入，没有配置多级缓存时为 null
     */
    @Autowired(required = false)
    private CacheManager cacheManager;

    /**
     * 当前实例 ID
     */
    @Getter
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    public MultiLevelCacheService(RedisUtils redisUtils,
                                   StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper) {
        this.redisUtils = redisUtils;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取缓存（支持多级查询）
     */
    public <T> T get(String cacheName, String key, Class<T> type, String redisKey,
                     long redisTtl, Supplier<T> loader) {
        // 1. 查询 L1 (Caffeine) - 如果有配置
        if (cacheManager != null) {
            T value = getFromL1(cacheName, key, type);
            if (value != null) {
                log.debug("[多级缓存] L1 命中: cacheName={}, key={}", cacheName, key);
                return value;
            }
        }

        // 2. 查询 L2 (Redis)
        T value = getFromL2(redisKey, type);
        if (value != null) {
            log.debug("[缓存] L2 命中: cacheName={}, key={}", cacheName, key);
            // 回填 L1
            if (cacheManager != null) {
                putToL1(cacheName, key, value);
            }
            return value;
        }

        // 3. 加载数据
        if (loader != null) {
            value = loader.get();
            if (value != null) {
                put(cacheName, key, redisKey, value, redisTtl);
            }
        }

        return value;
    }

    /**
     * 写入缓存
     */
    public <T> void put(String cacheName, String key, String redisKey, T value, long redisTtl) {
        // 写入 L1
        if (cacheManager != null) {
            putToL1(cacheName, key, value);
        }
        // 写入 L2
        putToL2(redisKey, value, redisTtl);
    }

    /**
     * 清除缓存
     */
    public void evict(String cacheName, String key, String redisKey) {
        // 清除本地 L1
        if (cacheManager != null) {
            evictL1(cacheName, key);
        }
        // 清除 L2
        evictL2(redisKey);
        // 发布失效消息通知其他实例
        if (cacheManager != null) {
            publishEviction(cacheName, key, false);
        }
    }

    /**
     * Pattern 清除缓存
     */
    public void evictByPattern(String cacheName, String keyPattern, String redisKeyPattern) {
        if (cacheManager != null) {
            evictL1ByPattern(cacheName, keyPattern);
            publishEviction(cacheName, keyPattern, true);
        }
    }

    // ==================== L1 (Caffeine) 操作 ====================

    @SuppressWarnings("unchecked")
    private <T> T getFromL1(String cacheName, String key, Class<T> type) {
        if (cacheManager == null) {
            return null;
        }
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
        if (cacheManager == null) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    private void evictL1(String cacheName, String key) {
        if (cacheManager == null) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
            log.debug("[多级缓存] 清除 L1: cacheName={}, key={}", cacheName, key);
        }
    }

    private void evictL1ByPattern(String cacheName, String pattern) {
        if (cacheManager == null) {
            return;
        }
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

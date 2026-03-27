package com.ticketbooking.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.ticketbooking.common.constant.CacheConstant;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 缓存失效监听器
 * 订阅 Redis Pub/Sub 消息，清除本地 Caffeine 缓存
 */
@Slf4j
public class CacheEvictionListener implements MessageListener {

    @Setter
    @Autowired
    private CacheManager cacheManager;

    @Setter
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            CacheEvictionMessage evictionMessage = objectMapper.readValue(json, CacheEvictionMessage.class);

            log.debug("[缓存失效] 收到消息: key={}, cacheName={}, source={}",
                    evictionMessage.getKey(),
                    evictionMessage.getCacheName(),
                    evictionMessage.getSourceInstanceId());

            // 清除本地 Caffeine 缓存
            evictLocalCache(evictionMessage);

        } catch (Exception e) {
            log.error("[缓存失效] 处理消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 清除本地 Caffeine 缓存
     */
    private void evictLocalCache(CacheEvictionMessage message) {
        String cacheName = message.getCacheName();
        String key = message.getKey();

        if (cacheName == null || key == null) {
            log.warn("[缓存失效] 无效的缓存名称或 key");
            return;
        }

        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("[缓存失效] 未找到缓存: {}", cacheName);
            return;
        }

        if (message.isPatternMatch()) {
            // Pattern 匹配清除 - 需要遍历清除
            evictByPattern(cache, key);
        } else {
            // 单 key 清除
            cache.evict(key);
            log.info("[缓存失效] 清除本地缓存: cacheName={}, key={}", cacheName, key);
        }
    }

    /**
     * Pattern 匹配清除
     * Caffeine 不支持 pattern，需要手动遍历
     */
    private void evictByPattern(org.springframework.cache.Cache cache, String pattern) {
        // 获取底层 Caffeine Cache
        Object nativeCache = cache.getNativeCache();
        if (nativeCache instanceof Cache) {
            @SuppressWarnings("unchecked")
            Cache<Object, Object> caffeineCache = (Cache<Object, Object>) nativeCache;

            // 构建前缀匹配
            String prefix = pattern.replace("*", "");

            caffeineCache.asMap().keySet().removeIf(key -> {
                String keyStr = String.valueOf(key);
                boolean matches = keyStr.startsWith(prefix);
                if (matches) {
                    log.debug("[缓存失效] Pattern 匹配清除: key={}", keyStr);
                }
                return matches;
            });
        }
    }
}

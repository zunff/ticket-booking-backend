package com.ticketbooking.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ticketbooking.common.constant.CacheConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.utils.RedisUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.TimeUnit;

/**
 * 用户缓存配置
 *
 * 启用方式：在 application.yaml 中配置 multi-level-cache.user.enabled=true
 * 适用服务：ticket-user-service
 */
@Configuration
@ConditionalOnClass(Caffeine.class)
@ConditionalOnProperty(name = "multi-level-cache.user.enabled", havingValue = "true")
public class UserCacheConfig {

    @Bean
    public Cache<String, Object> userLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(CacheConstant.USER_CACHE_MAX_SIZE)
                .expireAfterWrite(CacheConstant.USER_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Bean
    public MultiLevelCacheService multiLevelCacheService(
            RedisUtils redisUtils,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Cache<String, Object> userLocalCache) {
        return new MultiLevelCacheService(
                redisUtils,
                redisTemplate,
                objectMapper,
                userLocalCache,
                CacheConstant.CACHE_USER
        );
    }

    @Bean
    public CacheEvictionListener cacheEvictionListener(
            MultiLevelCacheService cacheService,
            ObjectMapper objectMapper) {
        return new CacheEvictionListener(
                cacheService,
                objectMapper,
                cacheService.getInstanceId()
        );
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory,
            CacheEvictionListener cacheEvictionListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(cacheEvictionListener, new ChannelTopic(CacheConstant.CACHE_EVICTION_CHANNEL));
        return container;
    }
}

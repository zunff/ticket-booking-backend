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
 * 演唱会缓存配置
 *
 * 启用方式：在 application.yaml 中配置 multi-level-cache.concert.enabled=true
 * 适用服务：ticket-service
 */
@Configuration
@ConditionalOnClass(Caffeine.class)
@ConditionalOnProperty(name = "multi-level-cache.concert.enabled", havingValue = "true")
public class ConcertCacheConfig {

    @Bean
    public Cache<String, Object> concertLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(CacheConstant.CONCERT_CACHE_MAX_SIZE)
                .expireAfterWrite(CacheConstant.CONCERT_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Bean
    public Cache<String, Object> ticketGradeLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(CacheConstant.TICKET_GRADE_CACHE_MAX_SIZE)
                .expireAfterWrite(CacheConstant.TICKET_GRADE_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Bean
    public MultiLevelCacheService multiLevelCacheService(
            RedisUtils redisUtils,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Cache<String, Object> concertLocalCache) {
        // 使用 concertLocalCache 作为主缓存
        return new MultiLevelCacheService(
                redisUtils,
                redisTemplate,
                objectMapper,
                concertLocalCache,
                CacheConstant.CACHE_CONCERT
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

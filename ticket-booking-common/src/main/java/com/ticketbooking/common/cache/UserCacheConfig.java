package com.ticketbooking.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.ticketbooking.common.constant.CacheConstant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import java.util.concurrent.TimeUnit;

/**
 * 用户缓存配置
 *
 * 启用方式：在 application.yaml 中配置 multi-level-cache.user.enabled=true
 * 适用服务：ticket-user-service
 */
@Configuration
@EnableCaching
@ConditionalOnClass(Caffeine.class)
@ConditionalOnProperty(name = "multi-level-cache.user.enabled", havingValue = "true")
public class UserCacheConfig {

    @Bean
    public Caffeine<Object, Object> userCaffeine() {
        return Caffeine.newBuilder()
                .maximumSize(CacheConstant.USER_CACHE_MAX_SIZE)
                .expireAfterWrite(CacheConstant.USER_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .recordStats();
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.registerCustomCache(CacheConstant.CACHE_USER, userCaffeine().build());
        return cacheManager;
    }

    @Bean
    public CacheEvictionListener cacheEvictionListener() {
        return new CacheEvictionListener();
    }

    @Bean
    public MessageListenerAdapter messageListenerAdapter(CacheEvictionListener listener) {
        return new MessageListenerAdapter(listener, "onMessage");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory,
            MessageListenerAdapter messageListenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(messageListenerAdapter, new ChannelTopic(CacheConstant.CACHE_EVICTION_CHANNEL));
        return container;
    }
}

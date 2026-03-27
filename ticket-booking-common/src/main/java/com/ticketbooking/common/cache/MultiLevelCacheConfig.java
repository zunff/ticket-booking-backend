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
 * 多级缓存配置
 */
@Configuration
@EnableCaching
@ConditionalOnClass(Caffeine.class)
@ConditionalOnProperty(name = "spring.data.redis.host")
public class MultiLevelCacheConfig {

    /**
     * 用户信息 Caffeine Cache
     */
    @Bean
    public Caffeine<Object, Object> userCaffeine() {
        return Caffeine.newBuilder()
                .maximumSize(CacheConstant.USER_CACHE_MAX_SIZE)
                .expireAfterWrite(CacheConstant.USER_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .recordStats();
    }

    /**
     * 演唱会信息 Caffeine Cache
     */
    @Bean
    public Caffeine<Object, Object> concertCaffeine() {
        return Caffeine.newBuilder()
                .maximumSize(CacheConstant.CONCERT_CACHE_MAX_SIZE)
                .expireAfterWrite(CacheConstant.CONCERT_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .recordStats();
    }

    /**
     * 票价档位 Caffeine Cache
     */
    @Bean
    public Caffeine<Object, Object> ticketGradeCaffeine() {
        return Caffeine.newBuilder()
                .maximumSize(CacheConstant.TICKET_GRADE_CACHE_MAX_SIZE)
                .expireAfterWrite(CacheConstant.TICKET_GRADE_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .recordStats();
    }

    /**
     * Caffeine Cache Manager
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(userCaffeine());
        cacheManager.registerCustomCache(CacheConstant.CACHE_USER, userCaffeine().build());
        cacheManager.registerCustomCache(CacheConstant.CACHE_CONCERT, concertCaffeine().build());
        cacheManager.registerCustomCache(CacheConstant.CACHE_TICKET_GRADE, ticketGradeCaffeine().build());
        return cacheManager;
    }

    /**
     * 缓存失效监听器
     */
    @Bean
    public CacheEvictionListener cacheEvictionListener() {
        return new CacheEvictionListener();
    }

    /**
     * 消息监听适配器
     */
    @Bean
    public MessageListenerAdapter messageListenerAdapter(CacheEvictionListener listener) {
        return new MessageListenerAdapter(listener, "onMessage");
    }

    /**
     * Redis 消息监听容器
     */
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

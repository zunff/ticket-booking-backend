package com.ticketbooking.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;

/**
 * 缓存失效监听器
 * 订阅 Redis Pub/Sub 消息，清除本地 Caffeine 缓存
 */
@Slf4j
@RequiredArgsConstructor
public class CacheEvictionListener implements MessageListener {

    private final MultiLevelCacheService cacheService;
    private final ObjectMapper objectMapper;
    private final String instanceId;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            CacheEvictionMessage evictionMessage = objectMapper.readValue(json, CacheEvictionMessage.class);

            // 忽略自己发送的消息
            if (instanceId.equals(evictionMessage.getSourceInstanceId())) {
                return;
            }

            log.debug("[缓存失效] 收到消息: key={}, cacheName={}, source={}",
                    evictionMessage.getKey(),
                    evictionMessage.getCacheName(),
                    evictionMessage.getSourceInstanceId());

            // 清除本地缓存
            cacheService.handleRemoteEviction(evictionMessage.getKey());

        } catch (Exception e) {
            log.error("[缓存失效] 处理消息失败: {}", e.getMessage(), e);
        }
    }
}

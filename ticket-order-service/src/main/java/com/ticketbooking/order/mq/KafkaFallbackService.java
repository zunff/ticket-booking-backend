package com.ticketbooking.order.mq;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.map.MapUtil;
import com.ticketbooking.common.constant.RedisKeyConstants;
import com.ticketbooking.common.mq.TicketOrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 降级服务
 * 实现两层降级策略：
 * 1. 优先写入 Redis Stream（持久化、可恢复）
 * 2. Redis 不可用时降级到本地内存队列
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaFallbackService {

    private final StringRedisTemplate stringRedisTemplate;
    private final LocalFallbackQueue localQueue;

    /**
     * 两层降级策略入口
     * @param message 订单消息
     */
    public void saveToFallback(TicketOrderMessage message) {
        // 第一层：尝试 Redis Stream
        if (tryRedisStreamFallback(message)) {
            log.info("Message saved to Redis Stream fallback: orderNo={}", message.getOrderNo());
            return;
        }

        // 第二层：降级到本地内存队列
        log.warn("Redis unavailable, fallback to local queue: orderNo={}", message.getOrderNo());
        localQueue.offer(message);
    }

    /**
     * 尝试写入 Redis Stream
     */
    private boolean tryRedisStreamFallback(TicketOrderMessage message) {
        try {
            Map<String, Object> objectMap = BeanUtil.beanToMap(message, false, true);
            Map<String, String> messageMap = new HashMap<>();
            objectMap.forEach((k, v) -> messageMap.put(k, v != null ? String.valueOf(v) : ""));
            messageMap.put("timestamp", String.valueOf(System.currentTimeMillis()));

            stringRedisTemplate.opsForStream().add(RedisKeyConstants.KAFKA_FALLBACK_STREAM_KEY, messageMap);
            return true;
        } catch (Exception e) {
            log.error("Redis Stream fallback failed: {}", e.getMessage());
            return false;
        }
    }
}

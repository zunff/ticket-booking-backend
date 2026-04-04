package com.ticketbooking.order.job;

import cn.hutool.core.bean.BeanUtil;
import com.ticketbooking.common.constant.RedisKeyConstants;
import com.ticketbooking.common.mq.TicketOrderMessage;
import com.ticketbooking.order.config.KafkaTopicConfig;
import com.ticketbooking.order.mq.LocalFallbackQueue;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Kafka 降级消息重试任务
 * 统一处理 Redis Stream 和本地队列中的降级消息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaFallbackRetryJob {

    private final StringRedisTemplate stringRedisTemplate;
    private final KafkaTemplate<String, TicketOrderMessage> kafkaTemplate;
    private final LocalFallbackQueue localQueue;

    private static final int MAX_BATCH = 50;

    /**
     * 重试降级消息（Redis Stream + 本地队列）
     * JobHandler: retryKafkaFallback
     * 建议配置：每 10 秒执行一次
     */
    @XxlJob("retryKafkaFallback")
    public void retryFallbackMessages() {
        long startTime = System.currentTimeMillis();
        int redisRetried = 0;
        int redisFailed = 0;
        int localRetried = 0;
        int localFailed = 0;

        try {
            // 1. 重试 Redis Stream 中的消息
            int[] redisResult = retryRedisStreamMessages();
            redisRetried = redisResult[0];
            redisFailed = redisResult[1];

            // 2. 重试本地队列中的消息
            int[] localResult = retryLocalQueueMessages();
            localRetried = localResult[0];
            localFailed = localResult[1];

            // 3. 汇总结果
            long cost = System.currentTimeMillis() - startTime;
            int totalRetried = redisRetried + localRetried;
            int totalFailed = redisFailed + localFailed;

            if (totalRetried == 0 && totalFailed == 0) {
                XxlJobHelper.handleSuccess("无降级消息需要重试");
            } else {
                String result = String.format(
                        "重试完成: Redis[成功=%d,失败=%d] 本地[成功=%d,失败=%d,剩余=%d] 耗时=%dms",
                        redisRetried, redisFailed, localRetried, localFailed, localQueue.size(), cost);
                XxlJobHelper.handleSuccess(result);
                log.info("Fallback retry completed: {}", result);
            }

        } catch (Exception e) {
            log.error("Retry fallback messages failed", e);
            XxlJobHelper.handleFail("重试失败: " + e.getMessage());
        }
    }

    /**
     * 重试 Redis Stream 中的消息
     */
    private int[] retryRedisStreamMessages() {
        int retried = 0;
        int failed = 0;

        try {
            List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                    .range(RedisKeyConstants.KAFKA_FALLBACK_STREAM_KEY, Range.closed("-", "+"));

            if (records == null || records.isEmpty()) {
                return new int[]{0, 0};
            }

            log.info("Found {} fallback messages in Redis Stream", records.size());

            for (MapRecord<String, Object, Object> record : records) {
                if (retried + failed >= MAX_BATCH) {
                    break;
                }

                try {
                    Map<Object, Object> messageData = record.getValue();
                    TicketOrderMessage message = mapToMessage(messageData);
                    if (message != null && retrySendMessage(message)) {
                        stringRedisTemplate.opsForStream().delete(
                                RedisKeyConstants.KAFKA_FALLBACK_STREAM_KEY, record.getId().getValue());
                        retried++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    log.error("Error processing Redis fallback message: {}", e.getMessage());
                    failed++;
                }
            }
        } catch (Exception e) {
            log.error("Error reading Redis Stream: {}", e.getMessage());
        }

        return new int[]{retried, failed};
    }

    /**
     * 重试本地队列中的消息
     */
    private int[] retryLocalQueueMessages() {
        int retried = 0;
        int failed = 0;

        if (localQueue.isEmpty()) {
            return new int[]{0, 0};
        }

        log.info("Found {} messages in local fallback queue", localQueue.size());

        while (!localQueue.isEmpty() && retried + failed < MAX_BATCH) {
            TicketOrderMessage message = localQueue.poll();
            if (message == null) {
                break;
            }

            try {
                if (retrySendMessage(message)) {
                    retried++;
                } else {
                    localQueue.offer(message);
                    failed++;
                    break;  // Kafka 不可用，停止重试
                }
            } catch (Exception e) {
                log.error("Error retrying local queue message: {}", e.getMessage());
                localQueue.offer(message);
                failed++;
                break;
            }
        }

        return new int[]{retried, failed};
    }

    /**
     * 尝试重新发送消息到 Kafka
     */
    private boolean retrySendMessage(TicketOrderMessage message) {
        try {
            kafkaTemplate.send(KafkaTopicConfig.TICKET_ORDER_TOPIC, message.getOrderNo(), message)
                    .get(5, TimeUnit.SECONDS);
            log.info("Retry send success: orderNo={}", message.getOrderNo());
            return true;
        } catch (Exception e) {
            log.warn("Retry send failed: orderNo={}, error={}", message.getOrderNo(), e.getMessage());
            return false;
        }
    }

    /**
     * 将 Redis Stream 消息映射为 TicketOrderMessage
     */
    private TicketOrderMessage mapToMessage(Map<Object, Object> data) {
        try {
            return BeanUtil.toBeanIgnoreCase(data, TicketOrderMessage.class, false);
        } catch (Exception e) {
            log.error("Failed to map message: {}", e.getMessage());
            return null;
        }
    }
}

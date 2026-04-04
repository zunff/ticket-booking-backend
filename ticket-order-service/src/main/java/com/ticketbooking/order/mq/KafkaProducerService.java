package com.ticketbooking.order.mq;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.ticketbooking.common.mq.TicketOrderMessage;
import com.ticketbooking.order.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Kafka 生产者服务
 * 封装发送逻辑，集成 Sentinel 限流熔断保护
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, TicketOrderMessage> kafkaTemplate;
    private final KafkaFallbackService fallbackService;

    /**
     * Sentinel 资源名称
     */
    private static final String RESOURCE_NAME = "kafka-producer:ticket-order-topic";

    /**
     * 发送超时时间（秒）
     */
    private static final int SEND_TIMEOUT_SECONDS = 5;

    /**
     * 发送订单消息（带 Sentinel 保护）
     * @param message 订单消息
     */
    public void sendOrderMessage(TicketOrderMessage message) {
        Entry entry = null;
        try {
            // Sentinel 限流入口（使用 COMMON_API_GATEWAY 类型）
            entry = SphU.entry(RESOURCE_NAME, 0, EntryType.OUT);

            // 创建 ProducerRecord
            ProducerRecord<String, TicketOrderMessage> record =
                    new ProducerRecord<>(KafkaTopicConfig.TICKET_ORDER_TOPIC, message.getOrderNo(), message);

            // 异步发送
            CompletableFuture<SendResult<String, TicketOrderMessage>> future = kafkaTemplate.send(record);

            // 添加超时处理
            future.orTimeout(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .whenComplete((result, ex) -> handleSendResult(message, result, ex));

        } catch (BlockException e) {
            // 限流/熔断降级处理
            handleBlock(message, e);
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    /**
     * 同步发送订单消息（带 Sentinel 保护）
     * @param message 订单消息
     * @return 是否发送成功
     */
    public boolean sendOrderMessageSync(TicketOrderMessage message) {
        Entry entry = null;
        try {
            // Sentinel 限流入口
            entry = SphU.entry(RESOURCE_NAME, 0, EntryType.OUT);

            // 同步发送
            kafkaTemplate.send(KafkaTopicConfig.TICKET_ORDER_TOPIC, message.getOrderNo(), message)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Sync sent order message: orderNo={}", message.getOrderNo());
            return true;

        } catch (BlockException e) {
            // 限流/熔断降级处理
            handleBlock(message, e);
            return false;
        } catch (Exception e) {
            log.error("Sync send failed: orderNo={}, error={}", message.getOrderNo(), e.getMessage());
            fallbackService.saveToFallback(message);
            return false;
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    /**
     * 处理发送结果回调
     */
    private void handleSendResult(TicketOrderMessage message,
                                  SendResult<String, TicketOrderMessage> result,
                                  Throwable ex) {
        if (ex != null) {
            log.error("Failed to send order message: orderNo={}, error={}",
                    message.getOrderNo(), ex.getMessage());
            // 发送失败，写入降级队列
            fallbackService.saveToFallback(message);
        } else {
            log.debug("Successfully sent order message: orderNo={}, partition={}, offset={}",
                    message.getOrderNo(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        }
    }

    /**
     * 处理 Sentinel 限流/熔断
     */
    private void handleBlock(TicketOrderMessage message, BlockException e) {
        log.warn("Kafka producer blocked: orderNo={}, blockType={}",
                message.getOrderNo(), e.getClass().getSimpleName());

        // 写入降级队列
        fallbackService.saveToFallback(message);
    }

    /**
     * 获取资源名称
     */
    public static String getResourceName() {
        return RESOURCE_NAME;
    }
}

package com.ticketbooking.ticket.mq;

import com.ticketbooking.ticket.config.KafkaTopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header; // 只保留Spring的@Header注解
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class OrderDeadLetterConsumer {

    /**
     * 死信队列消费者：处理订单Topic的失败消息
     * 修复：添加了Acknowledgment手动提交，避免死信消息丢失
     */
    @KafkaListener(topics = KafkaTopicConfig.TICKET_ORDER_DLT, groupId = "ticket-order-dlt-group", concurrency = "5")
    public void handleDeadLetter(
            @Payload(required = false) TicketOrderMessage message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String originalTopic,
            @Header(KafkaHeaders.DLT_ORIGINAL_PARTITION) Integer originalPartition,
            @Header(KafkaHeaders.DLT_ORIGINAL_OFFSET) Long originalOffset,
            Headers headers,
            Acknowledgment acknowledgment
    ) {
        try {
            // 1. 提取异常信息（用全限定名org.apache.kafka.common.header.Header）
            String exceptionMessage = getExceptionMessage(headers);

            // 2. 记录详细的失败日志
            log.error("收到死信队列消息，原Topic={}, 原Partition={}, 原Offset={}, 异常信息={}, 消息内容={}",
                    originalTopic, originalPartition, originalOffset, exceptionMessage, message);

            // 3. 可选：发送告警（比如钉钉/企业微信告警）
            sendAlert(originalTopic, originalPartition, originalOffset, exceptionMessage, message);

            // 4. 可选：写入数据库待处理表
            saveToPendingTable(originalTopic, originalPartition, originalOffset, message, exceptionMessage);

            // 5. 手动提交Offset：处理完成后再提交，避免死信消息丢失
            acknowledgment.acknowledge();
            log.info("死信消息处理完成，已提交Offset，原Offset={}", originalOffset);
        } catch (Exception e) {
            log.error("死信消息处理失败，原Offset={}", originalOffset, e);
            // 死信队列处理失败，可选择nack重试（但要避免无限循环），或直接记录日志不重试
            // acknowledgment.nack(Duration.ofSeconds(10)); // 谨慎使用，避免死循环
        }
    }

    /**
     * 从Header中提取原异常信息
     * 修复：使用全限定名org.apache.kafka.common.header.Header
     */
    private String getExceptionMessage(Headers headers) {
        // 这里用全限定名，避免和Spring的@Header注解冲突
        org.apache.kafka.common.header.Header exceptionHeader =
                headers.lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        if (exceptionHeader != null) {
            return new String(exceptionHeader.value(), StandardCharsets.UTF_8);
        }
        return "未知异常";
    }

    private void sendAlert(String topic, Integer partition, Long offset, String exception, TicketOrderMessage message) {
        // 实现你的告警逻辑
    }

    private void saveToPendingTable(String topic, Integer partition, Long offset, TicketOrderMessage message, String exception) {
        // 实现你的兜底处理逻辑
    }
}
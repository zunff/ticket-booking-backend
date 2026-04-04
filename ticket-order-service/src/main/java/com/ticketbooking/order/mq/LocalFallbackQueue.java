package com.ticketbooking.order.mq;

import com.ticketbooking.common.mq.TicketOrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地内存降级队列
 * 当 Redis 也不可用时，作为最后一道防线
 * 使用有界队列防止 OOM
 */
@Slf4j
@Component
public class LocalFallbackQueue {

    /**
     * 有界阻塞队列，最大容量 10000 条消息
     */
    private final BlockingQueue<TicketOrderMessage> queue = new LinkedBlockingQueue<>(10000);

    /**
     * 丢弃计数器（监控用）
     */
    private final AtomicInteger droppedCount = new AtomicInteger(0);

    /**
     * 将消息放入队列
     * @param message 订单消息
     * @return 是否成功
     */
    public boolean offer(TicketOrderMessage message) {
        boolean success = queue.offer(message);
        if (!success) {
            int dropped = droppedCount.incrementAndGet();
            log.error("Local fallback queue is full, message dropped: orderNo={}, totalDropped={}",
                    message.getOrderNo(), dropped);
        }
        return success;
    }

    /**
     * 从队列取出消息（阻塞）
     */
    public TicketOrderMessage take() throws InterruptedException {
        return queue.take();
    }

    /**
     * 从队列取出消息（非阻塞）
     */
    public TicketOrderMessage poll() {
        return queue.poll();
    }

    /**
     * 获取当前队列大小
     */
    public int size() {
        return queue.size();
    }

    /**
     * 队列是否为空
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * 获取丢弃的消息数量
     */
    public int getDroppedCount() {
        return droppedCount.get();
    }

    /**
     * 重置丢弃计数器
     */
    public void resetDroppedCount() {
        droppedCount.set(0);
    }
}

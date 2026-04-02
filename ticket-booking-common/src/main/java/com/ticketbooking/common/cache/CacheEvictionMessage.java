package com.ticketbooking.common.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 缓存失效消息
 * 通过 Redis Pub/Sub 广播给所有实例
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheEvictionMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 要清除的缓存 key
     */
    private String key;

    /**
     * 发送者实例 ID（避免重复处理）
     */
    private String sourceInstanceId;

    /**
     * 缓存名称
     */
    private String cacheName;
}

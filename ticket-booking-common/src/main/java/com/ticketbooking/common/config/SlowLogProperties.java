package com.ticketbooking.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 慢日志配置属性
 */
@Data
@ConfigurationProperties(prefix = "slow-log")
public class SlowLogProperties {

    /**
     * 是否启用慢日志
     */
    private boolean enabled = true;

    /**
     * 慢请求阈值（毫秒）
     */
    private long thresholdMs = 1000;

    /**
     * 是否记录请求参数
     */
    private boolean logParams = true;
}

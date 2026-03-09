package com.ticketbooking.common.sentinel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

/**
 * Feign Fallback 工厂基类
 * 提供统一的降级日志记录
 */
@Slf4j
public abstract class FeignFallbackFactory<T> implements FallbackFactory<T> {

    protected final String serviceName;

    protected FeignFallbackFactory(String serviceName) {
        this.serviceName = serviceName;
    }

    protected void logFallback(Throwable cause) {
        log.error("[{}] Feign调用降级: {}", serviceName, cause.getMessage(), cause);
    }
}

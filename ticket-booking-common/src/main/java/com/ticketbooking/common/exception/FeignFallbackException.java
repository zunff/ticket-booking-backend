package com.ticketbooking.common.exception;

import com.ticketbooking.common.enums.ErrorCode;

/**
 * Feign Fallback 降级异常
 * <p>
 * 当服务降级时由 Fallback 抛出，全局异常处理器会捕获并返回友好的错误信息
 */
public class FeignFallbackException extends BusinessException {

    private final String serviceName;

    public FeignFallbackException(String serviceName, ErrorCode errorCode) {
        super(errorCode, String.format("[%s] %s", serviceName, errorCode.getMessage()));
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}

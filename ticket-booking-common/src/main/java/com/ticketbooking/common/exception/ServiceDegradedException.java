package com.ticketbooking.common.exception;

import com.ticketbooking.common.enums.ErrorCode;

/**
 * 服务降级异常
 * 当下游服务不可用或触发熔断降级时抛出
 * 此异常继承自 BusinessException，不会被纳入 Sentinel 熔断统计
 */
public class ServiceDegradedException extends BusinessException {

    private final String serviceName;

    public ServiceDegradedException(String serviceName) {
        super(ErrorCode.SERVICE_DEGRADED, String.format("[%s] %s", serviceName, ErrorCode.SERVICE_DEGRADED.getMessage()));
        this.serviceName = serviceName;
    }

    public ServiceDegradedException(String serviceName, String message) {
        super(ErrorCode.SERVICE_DEGRADED, message);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}

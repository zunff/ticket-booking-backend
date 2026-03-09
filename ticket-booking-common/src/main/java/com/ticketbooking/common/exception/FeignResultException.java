package com.ticketbooking.common.exception;

import com.ticketbooking.common.enums.ErrorCode;

/**
 * Feign 调用结果异常
 * <p>
 * 当远程服务返回非成功状态码时由 FeignResultDecoder 抛出
 */
public class FeignResultException extends BusinessException {

    private final String serviceName;

    public FeignResultException(String serviceName, int code, String message) {
        super(code, message);
        this.serviceName = serviceName;
    }

    public FeignResultException(String serviceName, ErrorCode errorCode) {
        super(errorCode);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}

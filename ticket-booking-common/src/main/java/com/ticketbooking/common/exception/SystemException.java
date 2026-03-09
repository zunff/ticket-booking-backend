package com.ticketbooking.common.exception;

import com.ticketbooking.common.enums.ErrorCode;
import lombok.Getter;

/**
 * 系统异常 - 用于 5xx 错误，纳入 Sentinel 熔断统计
 */
@Getter
public class SystemException extends RuntimeException {
    private final int code;
    private final String message;

    public SystemException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    public SystemException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.message = message;
    }

    public SystemException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
        this.message = message;
    }
}

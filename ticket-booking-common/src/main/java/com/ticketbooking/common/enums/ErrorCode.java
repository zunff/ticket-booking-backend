package com.ticketbooking.common.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {
    
    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "方法不允许"),
    SYSTEM_ERROR(500, "系统异常"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),
    
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    USER_PASSWORD_ERROR(1003, "密码错误"),
    
    TICKET_NOT_FOUND(2001, "票务信息不存在"),
    TICKET_STOCK_NOT_ENOUGH(2002, "库存不足"),
    TICKET_SOLD_OUT(2003, "票已售罄"),
    TICKET_NOT_AVAILABLE(2004, "票务暂不可购买"),
    
    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_ALREADY_EXISTS(3002, "订单已存在"),
    ORDER_ALREADY_PAID(3003, "订单已支付"),
    ORDER_ALREADY_CANCELLED(3004, "订单已取消"),
    
    ALREADY_BOUGHT(4001, "您已经购买过该票，不能重复购买"),
    SYSTEM_BUSY(4002, "系统繁忙，请稍后重试"),
    RATE_LIMITED(4003, "请求过于频繁，请稍后重试"),
    
    TOKEN_INVALID(5001, "Token无效"),
    TOKEN_EXPIRED(5002, "Token已过期"),
    NO_PERMISSION(5003, "无权限访问");
    
    private final int code;
    private final String message;
    
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

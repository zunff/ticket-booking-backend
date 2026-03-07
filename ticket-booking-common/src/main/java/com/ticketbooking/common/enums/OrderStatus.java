package com.ticketbooking.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    
    PENDING(1, "待支付"),
    PAID(2, "已支付"),
    CANCELLED(3, "已取消"),
    FAILED(4, "失败");
    
    private final int code;
    private final String desc;
}


package com.ticketbooking.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {
    
    ACTIVE(1, "正常"),
    INACTIVE(2, "禁用"),
    DELETED(3, "已删除");
    
    private final int code;
    private final String desc;
}

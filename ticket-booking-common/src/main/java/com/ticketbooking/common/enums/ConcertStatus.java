package com.ticketbooking.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConcertStatus {
    
    PENDING(1, "待售"),
    ON_SALE(2, "开售中"),
    ENDED(3, "已结束");
    
    private final int code;
    private final String desc;
    
    public static ConcertStatus fromCode(int code) {
        for (ConcertStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid ConcertStatus code: " + code);
    }
}

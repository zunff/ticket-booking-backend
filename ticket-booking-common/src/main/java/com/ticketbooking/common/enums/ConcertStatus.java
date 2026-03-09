package com.ticketbooking.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConcertStatus {

    CLOSED(0, "已关闭"),
    ON_SALE(1, "开售中");

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

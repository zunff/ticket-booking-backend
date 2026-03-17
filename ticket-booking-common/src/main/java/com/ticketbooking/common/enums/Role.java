package com.ticketbooking.common.enums;

import lombok.Getter;

@Getter
public enum Role {
    USER("普通用户"),
    ADMIN("管理员");

    private final String description;

    Role(String description) {
        this.description = description;
    }
}

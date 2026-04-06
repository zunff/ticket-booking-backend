package com.ticketbooking.gateway.model;

import lombok.Getter;

public class CachedJwt {
    @Getter
    private final Long userId;
    @Getter
    private final String username;
    @Getter
    private final boolean admin;
    private final long expiresAt;

    public CachedJwt(Long userId, String username, boolean admin, long expiresAt) {
        this.userId = userId;
        this.username = username;
        this.admin = admin;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
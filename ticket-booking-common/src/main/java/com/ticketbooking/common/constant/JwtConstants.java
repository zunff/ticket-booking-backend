package com.ticketbooking.common.constant;

public final class JwtConstants {
    
    private JwtConstants() {
    }
    
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_IS_ADMIN = "isAdmin";
    
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_IS_ADMIN = "X-Is-Admin";
    
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String HEADER_AUTHORIZATION = "Authorization";
}

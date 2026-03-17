package com.ticketbooking.common.utils;

import com.ticketbooking.common.constant.JwtConstants;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtils {
    
    @Value("${jwt.secret:ticket-booking-jwt-secret-key-for-demo-purpose-only}")
    private String jwtSecret;
    
    private static final long EXPIRATION = 24 * 60 * 60 * 1000L;
    
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
    
    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, false);
    }
    
    public String generateToken(Long userId, String username, Boolean isAdmin) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtConstants.CLAIM_USER_ID, userId);
        claims.put(JwtConstants.CLAIM_USERNAME, username);
        claims.put(JwtConstants.CLAIM_IS_ADMIN, isAdmin != null && isAdmin);
        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(getKey())
                .compact();
    }
    
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    public Long getUserId(Claims claims) {
        Object userIdObj = claims.get(JwtConstants.CLAIM_USER_ID);
        if (userIdObj instanceof Integer) {
            return ((Integer) userIdObj).longValue();
        } else if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        }
        return Long.parseLong(String.valueOf(userIdObj));
    }
    
    public String getUsername(Claims claims) {
        return claims.get(JwtConstants.CLAIM_USERNAME, String.class);
    }
    
    public Boolean isAdmin(Claims claims) {
        return claims.get(JwtConstants.CLAIM_IS_ADMIN, Boolean.class);
    }
    
    public Claims validateToken(String token) {
        try {
            return parseToken(token);
        } catch (Exception e) {
            return null;
        }
    }
}

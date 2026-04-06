package com.ticketbooking.gateway.filter;

import com.ticketbooking.common.constant.JwtConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {
    
    @Value("${jwt.secret:ticket-booking-jwt-secret-key-for-demo-purpose-only}")
    private String jwtSecret;
    
    private SecretKey secretKey;
    
    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }
        
        String authHeader = request.getHeaders().getFirst(JwtConstants.HEADER_AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(JwtConstants.TOKEN_PREFIX)) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        
        String token = authHeader.substring(JwtConstants.TOKEN_PREFIX.length());
        
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            Long userId = claims.get(JwtConstants.CLAIM_USER_ID, Long.class);
            String username = claims.get(JwtConstants.CLAIM_USERNAME, String.class);
            Boolean isAdmin = claims.get(JwtConstants.CLAIM_IS_ADMIN, Boolean.class);
            
            if (path.contains("admin") && (isAdmin == null || !isAdmin)) {
                log.warn("Non-admin user {} attempted to access admin path: {}", username, path);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
            
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(JwtConstants.HEADER_USER_ID, String.valueOf(userId))
                    .header(JwtConstants.HEADER_USERNAME, username)
                    .header(JwtConstants.HEADER_IS_ADMIN, String.valueOf(isAdmin != null && isAdmin))
                    .build();
            
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
            
        } catch (Exception e) {
            log.error("JWT validation failed for path: {}, error: {}, message: {}", path, e.getClass().getSimpleName(), e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }
    
    private boolean isPublicPath(String path) {
        return path.startsWith("/actuator/") ||
               path.startsWith("/api/users/login") ||
               path.startsWith("/api/users/register") ||
               path.startsWith("/api/ticket") && !path.contains("/book") ||
               path.startsWith("/api/health") ||
               // Knife4j API 文档相关路径
               path.endsWith("/v3/api-docs") ||
               path.endsWith("/swagger-resources") ||
               path.endsWith("/swagger-resources/configuration/ui") ||
               path.endsWith("/swagger-resources/configuration/security") ||
               path.contains("/webjars/") ||
               path.contains("/doc.html") ||
               path.contains("/swagger-ui");
    }
    
    @Override
    public int getOrder() {
        return -100;
    }
}

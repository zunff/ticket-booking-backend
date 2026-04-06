package com.ticketbooking.gateway.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.ticketbooking.common.constant.JwtConstants;
import com.ticketbooking.gateway.model.CachedJwt;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
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
import java.time.Duration;

@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String CACHE_KEY_PREFIX = "jwt:";

    @Value("${jwt.secret:ticket-booking-jwt-secret-key-for-demo-purpose-only}")
    private String jwtSecret;

    @Value("${jwt.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${jwt.cache.expire-minutes:5}")
    private int cacheExpireMinutes;

    @Value("${jwt.cache.max-size:10000}")
    private int cacheMaxSize;

    private SecretKey secretKey;

    private Cache<String, CachedJwt> tokenCache;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        if (cacheEnabled) {
            this.tokenCache = Caffeine.newBuilder()
                    .expireAfterWrite(Duration.ofMinutes(cacheExpireMinutes))
                    .maximumSize(cacheMaxSize)
                    .recordStats()
                    .build();
            log.info("JWT token cache initialized: expireMinutes={}, maxSize={}", cacheExpireMinutes, cacheMaxSize);
        }
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
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(JwtConstants.TOKEN_PREFIX.length());

        try {
            CachedJwt cachedJwt = validateAndGetClaims(token);

            if (path.contains("admin") && !cachedJwt.isAdmin()) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(JwtConstants.HEADER_USER_ID, String.valueOf(cachedJwt.getUserId()))
                    .header(JwtConstants.HEADER_USERNAME, cachedJwt.getUsername())
                    .header(JwtConstants.HEADER_IS_ADMIN, String.valueOf(cachedJwt.isAdmin()))
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            log.debug("JWT expired for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        } catch (Exception e) {
            log.debug("JWT validation failed for path: {}, error: {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private CachedJwt validateAndGetClaims(String token) {
        if (!cacheEnabled || tokenCache == null) {
            return parseAndValidate(token);
        }

        String cacheKey = CACHE_KEY_PREFIX + token.hashCode();
        CachedJwt cached = tokenCache.getIfPresent(cacheKey);

        if (cached != null) {
            if (cached.isExpired()) {
                tokenCache.invalidate(cacheKey);
                log.debug("JWT cache entry expired, invalidating");
                throw new ExpiredJwtException(null, null, "JWT expired");
            }
            return cached;
        }

        CachedJwt result = parseAndValidate(token);
        tokenCache.put(cacheKey, result);
        return result;
    }

    private CachedJwt parseAndValidate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = claims.get(JwtConstants.CLAIM_USER_ID, Long.class);
        String username = claims.get(JwtConstants.CLAIM_USERNAME, String.class);
        Boolean isAdmin = claims.get(JwtConstants.CLAIM_IS_ADMIN, Boolean.class);
        long expiresAt = claims.getExpiration().getTime();

        return new CachedJwt(userId, username, isAdmin != null && isAdmin, expiresAt);
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/actuator/") ||
               path.startsWith("/api/users/login") ||
               path.startsWith("/api/users/register") ||
               path.startsWith("/api/ticket") && !path.contains("/book") ||
               path.startsWith("/api/health") ||
               path.endsWith("/v3/api-docs") ||
               path.endsWith("/swagger-resources") ||
               path.endsWith("/swagger-resources/configuration/ui") ||
               path.endsWith("/swagger-resources/configuration/security") ||
               path.contains("/webjars/") ||
               path.contains("/doc.html") ||
               path.contains("/swagger-ui");
    }

    public CacheStats getCacheStats() {
        return tokenCache != null ? tokenCache.stats() : null;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}

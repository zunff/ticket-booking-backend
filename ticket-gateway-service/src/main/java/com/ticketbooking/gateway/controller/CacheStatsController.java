package com.ticketbooking.gateway.controller;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.ticketbooking.gateway.filter.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/actuator")
@RequiredArgsConstructor
public class CacheStatsController {

    private final JwtAuthFilter jwtAuthFilter;

    @GetMapping("/jwt-cache")
    public ResponseEntity<Map<String, Object>> getJwtCacheStats() {
        CacheStats stats = jwtAuthFilter.getCacheStats();
        Map<String, Object> result = new HashMap<>();
        
        if (stats == null) {
            result.put("enabled", false);
            result.put("message", "JWT cache is disabled");
        } else {
            result.put("enabled", true);
            result.put("hitCount", stats.hitCount());
            result.put("missCount", stats.missCount());
            result.put("hitRate", String.format("%.2f%%", stats.hitRate() * 100));
            result.put("evictionCount", stats.evictionCount());
            result.put("loadSuccessCount", stats.loadSuccessCount());
            result.put("loadFailureCount", stats.loadFailureCount());
            result.put("averageLoadPenalty", String.format("%.2f ms", stats.averageLoadPenalty() / 1_000_000.0));
        }
        
        return ResponseEntity.ok(result);
    }
}

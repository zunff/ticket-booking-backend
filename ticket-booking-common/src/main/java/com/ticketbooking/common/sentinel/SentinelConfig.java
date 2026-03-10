package com.ticketbooking.common.sentinel;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.adapter.web.common.UrlCleaner;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Pattern;

/**
 * Sentinel 全局配置
 */
@Slf4j
@Configuration
public class SentinelConfig {

    private static final Pattern ID_PATTERN = Pattern.compile("/\\d+");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * URL 归一化处理器 - RESTful 接口资源名统一
     */
    @Bean
    public UrlCleaner urlCleaner() {
        return url -> {
            // 将 /api/concerts/1 归一化为 /api/concerts/{id}
            String normalized = ID_PATTERN.matcher(url).replaceAll("/{id}");
            log.debug("URL normalized: {} -> {}", url, normalized);
            return normalized;
        };
    }

    /**
     * 自定义限流熔断响应处理器
     */
    @Bean
    public BlockExceptionHandler blockExceptionHandler() {
        return (HttpServletRequest request, HttpServletResponse response, String resourceName, BlockException e) -> {
            log.warn("Sentinel block: uri={}, resource={}, rule={}", request.getRequestURI(), resourceName, e.getRule());

            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");

            Result<Void> result = Result.error(ErrorCode.RATE_LIMITED);
            response.getWriter().write(OBJECT_MAPPER.writeValueAsString(result));
        };
    }
}

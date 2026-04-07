package com.ticketbooking.common.filter;

import com.ticketbooking.common.constant.TraceConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 业务服务 TraceId 过滤器
 * 从请求头获取 TraceId 并放入 MDC，用于日志追踪
 */
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // 1. 从请求头获取 TraceId
            String traceId = request.getHeader(TraceConstants.TRACE_ID_HEADER);

            // 2. 不存在则生成新的 TraceId
            if (traceId == null || traceId.isEmpty()) {
                traceId = generateTraceId();
            }

            // 3. 放入 MDC
            MDC.put(TraceConstants.TRACE_ID_KEY, traceId);

            // 4. 添加到响应头，方便追踪
            response.setHeader(TraceConstants.TRACE_ID_HEADER, traceId);

            filterChain.doFilter(request, response);
        } finally {
            // 5. 请求结束后清理 MDC
            MDC.remove(TraceConstants.TRACE_ID_KEY);
        }
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

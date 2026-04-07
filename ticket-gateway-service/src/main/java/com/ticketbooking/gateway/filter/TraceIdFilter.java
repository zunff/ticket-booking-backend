package com.ticketbooking.gateway.filter;

import com.ticketbooking.common.constant.TraceConstants;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Gateway TraceId 过滤器
 * 生成/传递 TraceId，用于跨服务请求追踪
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 从请求头获取或生成 TraceId
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
        }

        // 2. 放入 MDC (当前线程)
        MDC.put(TraceConstants.TRACE_ID_KEY, traceId);

        // 3. 添加到转发请求头
        String finalTraceId = traceId;
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TraceConstants.TRACE_ID_HEADER, finalTraceId)
                .build();

        // 4. 添加到响应头
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().set(TraceConstants.TRACE_ID_HEADER, finalTraceId);

        // 5. 继续过滤链，确保完成后清理 MDC
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doFinally(signalType -> MDC.remove(TraceConstants.TRACE_ID_KEY));
    }

    private String generateTraceId() {
        // 使用 UUID 生成 TraceId，去掉横线使其更短
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public int getOrder() {
        // 在 JwtAuthFilter (-100) 之前执行
        return -200;
    }
}

package com.ticketbooking.gateway.config;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.ResourceTypeConstants;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.result.Result;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 网关 Sentinel 配置
 */
@Slf4j
@Configuration
public class SentinelGatewayConfig {

    private final ObjectMapper objectMapper;

    public SentinelGatewayConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 匹配 URL 中的数字 ID，例如 /api/stock/1/3 -> /api/stock/{id}/{id}
    private static final Pattern ID_PATTERN = Pattern.compile("/\\d+(?=/|$)");
    private static final String SENTINEL_CONTEXT_PREFIX = "sentinel_gateway_context$$";

    @PostConstruct
    public void init() {
        // 配置网关限流熔断响应
        GatewayCallbackManager.setBlockHandler((exchange, t) -> {
            log.warn("Gateway sentinel block: uri={}", exchange.getRequest().getURI());
            Result<Void> result = Result.error(ErrorCode.RATE_LIMITED);
            return ServerResponse
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(result));
        });
    }

    /**
     * 自定义 Sentinel Gateway Filter
     * 使用归一化的 URL 作为资源名称
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public GlobalFilter sentinelGatewayFilter() {
        return new GlobalFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                if (route == null) {
                    return chain.filter(exchange);
                }

                // 归一化资源名称
                String resourceName = normalizeResourceName(exchange);

                Entry entry = null;
                try {
                    String contextName = SENTINEL_CONTEXT_PREFIX + route.getId();
                    ContextUtil.enter(contextName);
                    entry = SphU.entry(resourceName, ResourceTypeConstants.COMMON_API_GATEWAY, EntryType.IN);
                    return chain.filter(exchange);
                } catch (BlockException e) {
                    log.warn("Gateway sentinel block: resource={}, route={}", resourceName, route.getId());
                    return handleBlockRequest(exchange);
                } finally {
                    if (entry != null) {
                        entry.exit();
                    }
                    ContextUtil.exit();
                }
            }

            private String normalizeResourceName(ServerWebExchange exchange) {
                String path = exchange.getRequest().getURI().getPath();
                return ID_PATTERN.matcher(path).replaceAll("/{id}");
            }

            private Mono<Void> handleBlockRequest(ServerWebExchange exchange) {
                Result<Void> result = Result.error(ErrorCode.RATE_LIMITED);
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                try {
                    String body = objectMapper.writeValueAsString(result);
                    return exchange.getResponse().writeWith(
                            Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes()))
                    );
                } catch (Exception ex) {
                    log.error("Error writing block response", ex);
                    return Mono.empty();
                }
            }
        };
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler(
            List<ViewResolver> viewResolvers,
            ServerCodecConfigurer serverCodecConfigurer) {
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }
}

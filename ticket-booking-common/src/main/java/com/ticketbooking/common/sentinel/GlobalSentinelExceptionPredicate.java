package com.ticketbooking.common.sentinel;

import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.util.function.Predicate;
import com.ticketbooking.common.exception.BusinessException;
import feign.FeignException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 全局 Sentinel 异常判断器
 * 实现 Predicate 接口并注册到 Tracer，用于判断哪些异常应该纳入熔断统计
 *
 * <p>Sentinel 1.8.8+ 支持 Tracer.setExceptionPredicate() 方法，
 * 通过此机制可以全局控制异常统计行为。</p>
 *
 * <p>异常过滤规则：</p>
 * <ul>
 *   <li>BusinessException - 不纳入统计（业务异常，非系统故障）</li>
 *   <li>FeignException 4xx - 不纳入统计（客户端错误）</li>
 *   <li>FeignException 5xx - 纳入统计（服务端错误）</li>
 *   <li>其他异常 - 纳入统计</li>
 * </ul>
 */
@Slf4j
@Component
public class GlobalSentinelExceptionPredicate implements Predicate<Throwable> {

    @PostConstruct
    public void init() {
        // 注册到 Sentinel Tracer
        Tracer.setExceptionPredicate(this);
        log.info("Sentinel ExceptionPredicate registered successfully");
    }

    /**
     * 判断异常是否应该纳入熔断统计
     *
     * @param throwable 异常
     * @return true-纳入统计（触发熔断计算）, false-不纳入统计（忽略）
     */
    @Override
    public boolean test(Throwable throwable) {
        // 业务异常不纳入熔断统计
        if (throwable instanceof BusinessException) {
            log.debug("BusinessException ignored for circuit breaker: {}", throwable.getMessage());
            return false;
        }

        // Feign 异常处理
        if (throwable instanceof FeignException feignException) {
            int status = feignException.status();
            // 仅 5xx 系统异常纳入统计，4xx 客户端错误忽略
            boolean shouldTrace = status >= 500;
            log.debug("FeignException status={}, shouldTrace={}", status, shouldTrace);
            return shouldTrace;
        }

        // 其他异常均纳入统计
        return true;
    }
}

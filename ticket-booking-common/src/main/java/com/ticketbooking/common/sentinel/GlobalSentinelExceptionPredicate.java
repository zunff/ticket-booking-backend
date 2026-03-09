package com.ticketbooking.common.sentinel;

import com.ticketbooking.common.exception.BusinessException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 全局 Sentinel 异常判断工具类
 * 用于判断哪些异常应该纳入熔断统计
 *
 * 注意：Sentinel 1.8.6 不支持自定义 ExceptionPredicate
 * 此类作为工具类使用，需要在具体熔断逻辑中手动调用
 */
@Slf4j
@Component
public class GlobalSentinelExceptionPredicate {

    /**
     * 判断异常是否应该纳入熔断统计
     * @param throwable 异常
     * @return true-纳入统计, false-不纳入统计
     */
    public boolean shouldCount(Throwable throwable) {
        // 业务异常不纳入熔断统计
        if (throwable instanceof BusinessException) {
            return false;
        }
        // Feign 业务异常不纳入统计
        if (throwable instanceof FeignException feignException) {
            int status = feignException.status();
            return status >= 500; // 仅5xx系统异常纳入统计
        }
        // 其他异常均纳入统计
        return true;
    }
}

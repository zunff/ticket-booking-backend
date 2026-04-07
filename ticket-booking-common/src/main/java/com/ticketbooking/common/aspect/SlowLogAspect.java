package com.ticketbooking.common.aspect;

import cn.hutool.json.JSONUtil;
import com.ticketbooking.common.config.SlowLogProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 慢日志切面
 * 记录超过阈值的请求
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "slow-log", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SlowLogProperties.class)
public class SlowLogAspect {

    private final SlowLogProperties slowLogProperties;

    /**
     * 单独的慢日志 Logger，便于单独配置输出
     */
    private static final org.slf4j.Logger SLOW_LOG = LoggerFactory.getLogger("SLOW_LOG");

    @Around("execution(* com.ticketbooking..controller..*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        try {
            return joinPoint.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            long threshold = slowLogProperties.getThresholdMs();

            if (elapsed >= threshold) {
                logSlowRequest(joinPoint, elapsed);
            }
        }
    }

    private void logSlowRequest(ProceedingJoinPoint joinPoint, long elapsed) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }

        HttpServletRequest request = attributes.getRequest();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        StringBuilder sb = new StringBuilder();
        sb.append("Slow Request: ");
        sb.append("[").append(signature.getDeclaringType().getSimpleName()).append(".");
        sb.append(signature.getName()).append("] ");
        sb.append(request.getMethod()).append(" ").append(request.getRequestURI());
        sb.append(" - ").append(elapsed).append("ms");

        // 记录请求参数
        if (slowLogProperties.isLogParams()) {
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                try {
                    sb.append(" | params: ").append(JSONUtil.toJsonStr(args));
                } catch (Exception e) {
                    sb.append(" | params: [serialization failed]");
                }
            }
        }

        SLOW_LOG.warn(sb.toString());
    }
}

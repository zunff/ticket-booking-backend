package com.ticketbooking.common.aspect;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.ticketbooking.common.annotation.UserRateLimit;
import com.ticketbooking.common.context.UserContext;
import com.ticketbooking.common.context.UserInfo;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.result.Result;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class UserRateLimitAspect {

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, UserRateLimit rateLimit) throws Throwable {

        UserInfo currentUser = UserContext.getUserInfo();
        if (currentUser == null) {
            return Result.error("请先登录");
        }

        // 2. 确定基础资源名
        String baseResourceName = getResourceName(joinPoint, rateLimit);
        
        // 3. 核心逻辑：根据用户等级，决定使用哪套限流规则 (资源名)
        Long userId = currentUser.getUserId();

        // 可以做到不同用户的限流规则
//        String actualResourceName;
//        boolean isVip = "VIP".equals(currentUser.getUserLevel());
//        if (isVip) {
//            actualResourceName = baseResourceName + ":vip";
//        } else {
//            actualResourceName = baseResourceName + ":normal";
//        }

        Entry entry = null;
        try {
            // 4. 执行限流
            // 参数说明：
            // actualResourceName: 如果是VIP，就是 "xxx:vip"，这个资源我们配置 QPS=10
            // userId: 传入用户ID，确保是针对这个具体ID的限流 (集群模式下需要注意，单机模式直接生效)
            entry = SphU.entry(baseResourceName, EntryType.IN, 1, userId);

            // 5. 执行业务
            return joinPoint.proceed();

        } catch (BlockException ex) {
            return Result.error(ErrorCode.TOO_MANY_REQUESTS);
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    private String getResourceName(ProceedingJoinPoint joinPoint, UserRateLimit rateLimit) {
        if (!rateLimit.resourceName().isEmpty()) {
            return rateLimit.resourceName();
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getDeclaringClass().getSimpleName() + ":" + method.getName();
    }
}
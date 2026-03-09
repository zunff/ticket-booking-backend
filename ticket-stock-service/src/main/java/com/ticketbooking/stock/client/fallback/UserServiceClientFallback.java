package com.ticketbooking.stock.client.fallback;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.FeignFallbackException;
import com.ticketbooking.common.sentinel.FeignFallbackFactory;
import com.ticketbooking.stock.client.UserServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * UserServiceClient 降级处理
 */
@Slf4j
@Component
public class UserServiceClientFallback extends FeignFallbackFactory<UserServiceClient> {

    public UserServiceClientFallback() {
        super("ticket-user-service");
    }

    @Override
    public UserServiceClient create(Throwable cause) {
        logFallback(cause);
        return userId -> {
            log.warn("[{}] 用户验证降级: userId={}", serviceName, userId);
            throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
        };
    }
}

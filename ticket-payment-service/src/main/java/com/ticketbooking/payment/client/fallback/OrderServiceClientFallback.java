package com.ticketbooking.payment.client.fallback;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.FeignFallbackException;
import com.ticketbooking.common.sentinel.FeignFallbackFactory;
import com.ticketbooking.payment.client.OrderServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderServiceClientFallback extends FeignFallbackFactory<OrderServiceClient> {

    public OrderServiceClientFallback() {
        super("ticket-order-service");
    }

    @Override
    public OrderServiceClient create(Throwable cause) {
        logFallback(cause);
        return orderNo -> {
            log.warn("[{}] 标记订单已支付降级: orderNo={}", serviceName, orderNo);
            throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
        };
    }
}

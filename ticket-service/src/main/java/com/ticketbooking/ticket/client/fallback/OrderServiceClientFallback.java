package com.ticketbooking.ticket.client.fallback;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.FeignFallbackException;
import com.ticketbooking.common.model.dto.DashboardStatsDTO;
import com.ticketbooking.common.sentinel.FeignFallbackFactory;
import com.ticketbooking.ticket.client.OrderServiceClient;
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
        return new OrderServiceClient() {
            @Override
            public DashboardStatsDTO getDashboardStats() {
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }

            @Override
            public Integer countUserPurchased(Long userId, Long concertId) {
                log.warn("[{}] 查询用户购买数量降级: userId={}, concertId={}", serviceName, userId, concertId);
                // 降级时返回 0，允许继续下单
                return 0;
            }
        };
    }
}

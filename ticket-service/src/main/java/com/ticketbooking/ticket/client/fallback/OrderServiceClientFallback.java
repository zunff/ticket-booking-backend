package com.ticketbooking.ticket.client.fallback;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.FeignFallbackException;
import com.ticketbooking.common.model.dto.DashboardStatsDTO;
import com.ticketbooking.common.sentinel.FeignFallbackFactory;
import com.ticketbooking.ticket.client.OrderServiceClient;
import org.springframework.stereotype.Component;

@Component
public class OrderServiceClientFallback extends FeignFallbackFactory<OrderServiceClient> {

    public OrderServiceClientFallback() {
        super("ticket-order-service");
    }

    @Override
    public OrderServiceClient create(Throwable cause) {
        return new OrderServiceClient() {
            @Override
            public DashboardStatsDTO getDashboardStats() {
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }
        };
    }
}

package com.ticketbooking.ticket.client;

import com.ticketbooking.common.model.dto.DashboardStatsDTO;
import com.ticketbooking.ticket.client.fallback.OrderServiceClientFallback;
import com.ticketbooking.ticket.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "ticket-order-service", path = "/internal/orders",
             configuration = FeignClientConfig.class,
             fallbackFactory = OrderServiceClientFallback.class)
public interface OrderServiceClient {

    @GetMapping("/dashboard-stats")
    DashboardStatsDTO getDashboardStats();
}

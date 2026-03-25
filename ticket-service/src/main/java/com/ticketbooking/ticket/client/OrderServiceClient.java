package com.ticketbooking.ticket.client;

import com.ticketbooking.common.model.dto.DashboardStatsDTO;
import com.ticketbooking.ticket.client.fallback.OrderServiceClientFallback;
import com.ticketbooking.ticket.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ticket-order-service", path = "/internal/orders",
             configuration = FeignClientConfig.class,
             fallbackFactory = OrderServiceClientFallback.class)
public interface OrderServiceClient {

    @GetMapping("/dashboard-stats")
    DashboardStatsDTO getDashboardStats();

    /**
     * 查询用户在演唱会的已购买数量
     */
    @GetMapping("/count-purchased")
    Integer countUserPurchased(@RequestParam("userId") Long userId,
                               @RequestParam("concertId") Long concertId);
}

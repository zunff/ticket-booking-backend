package com.ticketbooking.ticket.client;

import com.ticketbooking.common.model.dto.ConcertSalesDTO;
import com.ticketbooking.common.model.dto.DashboardStatsDTO;
import com.ticketbooking.common.model.dto.SalesDataDTO;
import com.ticketbooking.ticket.client.fallback.OrderServiceClientFallback;
import com.ticketbooking.ticket.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "ticket-order-service", path = "/order/internal",
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

    /**
     * 获取最近N天的销售数据
     */
    @GetMapping("/sales-data")
    List<SalesDataDTO> getSalesData(@RequestParam("days") Integer days);

    /**
     * 获取各演唱会的销售统计
     */
    @GetMapping("/concert-sales-stats")
    List<ConcertSalesDTO> getConcertSalesStats();
}

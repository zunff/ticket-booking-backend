package com.ticketbooking.order.client;

import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.order.client.fallback.StockServiceClientFallback;
import com.ticketbooking.order.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ticket-stock-service", path = "/internal/stock",
             configuration = FeignClientConfig.class,
             fallbackFactory = StockServiceClientFallback.class)
public interface StockServiceClient {

    @GetMapping
    StockDTO getStock(@RequestParam Long concertId, @RequestParam Long gradeId);
}

package com.ticketbooking.ticket.client;

import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.ticket.client.fallback.StockServiceClientFallback;
import com.ticketbooking.ticket.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "ticket-stock-service", path = "/internal/stock",
             configuration = FeignClientConfig.class,
             fallbackFactory = StockServiceClientFallback.class)
public interface StockServiceClient {

    @GetMapping("/batch/{concertId}")
    List<StockDTO> getStocksByConcertId(@PathVariable("concertId") Long concertId);

    @PostMapping("/init")
    void initStock(
            @RequestParam("concertId") Long concertId,
            @RequestParam("gradeId") Long gradeId,
            @RequestParam("totalStock") Integer totalStock
    );
}

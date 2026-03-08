package com.ticketbooking.ticket.client;

import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.ticket.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "ticket-stock-service", path = "/internal/stock",
             configuration = FeignClientConfig.class)
public interface StockServiceClient {

    @GetMapping("/batch/{concertId}")
    List<StockDTO> getStocksByConcertId(@PathVariable("concertId") Long concertId);
}

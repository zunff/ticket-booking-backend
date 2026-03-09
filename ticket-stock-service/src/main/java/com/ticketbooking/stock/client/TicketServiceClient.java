package com.ticketbooking.stock.client;

import com.ticketbooking.common.model.dto.TicketGradeDTO;
import com.ticketbooking.stock.client.fallback.TicketServiceClientFallback;
import com.ticketbooking.stock.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ticket-service", path = "/internal",
             configuration = FeignClientConfig.class,
             fallbackFactory = TicketServiceClientFallback.class)
public interface TicketServiceClient {

    @GetMapping("/grades/{id}")
    TicketGradeDTO getGradeById(@PathVariable("id") Long id);
}

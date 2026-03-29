package com.ticketbooking.order.client;

import com.ticketbooking.common.model.dto.TicketGradeDTO;
import com.ticketbooking.order.client.fallback.TicketServiceClientFallback;
import com.ticketbooking.order.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ticket-service", path = "/ticket/internal",
             configuration = FeignClientConfig.class,
             fallbackFactory = TicketServiceClientFallback.class)
public interface TicketServiceClient {

    @GetMapping("/grades/{id}")
    TicketGradeDTO getGradeById(@PathVariable("id") Long id);
}

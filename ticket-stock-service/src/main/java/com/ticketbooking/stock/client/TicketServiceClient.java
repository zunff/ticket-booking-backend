package com.ticketbooking.stock.client;

import com.ticketbooking.stock.model.dto.TicketDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ticket-service", path = "/api")
public interface TicketServiceClient {
    
    @GetMapping("/tickets/{id}")
    TicketDTO getTicketById(@PathVariable("id") Long id);
    
    @GetMapping("/tickets/{id}/stock")
    Integer getAvailableStock(@PathVariable("id") Long ticketId);
}

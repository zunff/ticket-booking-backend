package com.ticketbooking.stock.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ticket-user-service", path = "/api")
public interface UserServiceClient {
    
    @GetMapping("/users/{id}/validate")
    Boolean validateUser(@PathVariable("id") Long userId);
}

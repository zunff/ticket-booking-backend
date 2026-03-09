package com.ticketbooking.stock.client;

import com.ticketbooking.stock.client.fallback.UserServiceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ticket-user-service", path = "/api",
             fallbackFactory = UserServiceClientFallback.class)
public interface UserServiceClient {

    @GetMapping("/users/validate/{id}")
    Boolean validateUser(@PathVariable("id") Long userId);
}

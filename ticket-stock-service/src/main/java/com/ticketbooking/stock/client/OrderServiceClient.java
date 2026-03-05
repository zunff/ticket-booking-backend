package com.ticketbooking.stock.client;

import com.ticketbooking.stock.model.qo.CreateOrderQO;
import com.ticketbooking.stock.model.dto.OrderDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ticket-order-service", path = "/internal")
public interface OrderServiceClient {
    
    @GetMapping("/orders/{orderNo}")
    OrderDTO findByOrderNo(@PathVariable("orderNo") String orderNo);
    
    @PostMapping("/orders")
    OrderDTO createOrder(@RequestBody CreateOrderQO qo);
}

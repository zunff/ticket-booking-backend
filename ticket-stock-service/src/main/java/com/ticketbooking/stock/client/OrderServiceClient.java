package com.ticketbooking.stock.client;

import com.ticketbooking.common.model.dto.OrderDTO;
import com.ticketbooking.common.model.qo.CreateOrderQO;
import com.ticketbooking.stock.client.fallback.OrderServiceClientFallback;
import com.ticketbooking.stock.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ticket-order-service", path = "/internal",
             configuration = FeignClientConfig.class,
             fallbackFactory = OrderServiceClientFallback.class)
public interface OrderServiceClient {

    @GetMapping("/orders/{orderNo}")
    OrderDTO findByOrderNo(@PathVariable("orderNo") String orderNo);

    @PostMapping("/orders")
    OrderDTO createOrder(@RequestBody CreateOrderQO qo);
}

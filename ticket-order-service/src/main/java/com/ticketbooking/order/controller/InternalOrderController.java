package com.ticketbooking.order.controller;

import com.ticketbooking.common.model.dto.OrderDTO;
import com.ticketbooking.common.model.qo.CreateOrderQO;
import com.ticketbooking.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    @GetMapping("/{orderNo}")
    public OrderDTO findByOrderNo(@PathVariable String orderNo) {
        return orderService.findDTOByOrderNo(orderNo);
    }

    @PostMapping
    public OrderDTO createOrder(@RequestBody CreateOrderQO qo) {
        return orderService.createOrderDTO(qo);
    }
}

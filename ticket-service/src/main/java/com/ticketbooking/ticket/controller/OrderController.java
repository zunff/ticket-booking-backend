package com.ticketbooking.ticket.controller;

import com.ticketbooking.common.annotation.RequireAuth;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.ticket.entity.Order;
import com.ticketbooking.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    
    private final TicketService ticketService;
    
    @GetMapping("/{orderNo}")
    @RequireAuth
    public Result<Order> getOrderByOrderNo(@PathVariable String orderNo) {
        Order order = ticketService.getOrderByOrderNo(orderNo);
        return order != null ? Result.success(order) : Result.error(3001, "订单不存在");
    }
    
    @GetMapping("/user/{userId}")
    @RequireAuth
    public Result<List<Order>> getOrdersByUserId(@PathVariable Long userId) {
        return Result.success(ticketService.getOrdersByUserId(userId));
    }
}

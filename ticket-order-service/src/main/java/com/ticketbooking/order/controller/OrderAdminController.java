package com.ticketbooking.order.controller;

import com.ticketbooking.common.annotation.RequireAdmin;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.order.converter.OrderConverter;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.service.OrderService;
import com.ticketbooking.order.model.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class OrderAdminController {

    private final OrderService orderService;
    private final OrderConverter orderConverter;

    @GetMapping("/{orderNo}")
    @RequireAdmin
    public Result<OrderVO> getOrderByOrderNo(@PathVariable String orderNo) {
        Order order = orderService.findByOrderNo(orderNo);
        return Result.success(orderConverter.toVO(order));
    }

    @GetMapping("/user/{userId}")
    @RequireAdmin
    public Result<List<OrderVO>> getOrdersByUserId(@PathVariable Long userId) {
        List<Order> orders = orderService.findByUserId(userId);
        return Result.success(orderConverter.toVOList(orders));
    }
}

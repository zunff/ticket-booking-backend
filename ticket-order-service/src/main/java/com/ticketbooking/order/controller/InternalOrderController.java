package com.ticketbooking.order.controller;

import com.ticketbooking.common.model.dto.DashboardStatsDTO;
import com.ticketbooking.common.model.dto.OrderDTO;
import com.ticketbooking.common.model.qo.CreateOrderQO;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    @GetMapping("/{orderNo}")
    public Result<OrderDTO> findByOrderNo(@PathVariable String orderNo) {
        return Result.success(orderService.findDTOByOrderNo(orderNo));
    }

    @PostMapping
    public Result<OrderDTO> createOrder(@RequestBody CreateOrderQO qo) {
        return Result.success(orderService.createOrderDTO(qo));
    }

    /**
     * 获取仪表盘统计数据
     */
    @GetMapping("/dashboard-stats")
    public Result<DashboardStatsDTO> getDashboardStats() {
        return Result.success(orderService.getDashboardStats());
    }
}

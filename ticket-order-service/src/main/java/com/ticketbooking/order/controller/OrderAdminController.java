package com.ticketbooking.order.controller;

import com.ticketbooking.common.annotation.RequireAuth;
import com.ticketbooking.common.enums.Role;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.order.service.OrderService;
import com.ticketbooking.order.model.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class OrderAdminController {

    private final OrderService orderService;

    @GetMapping
    @RequireAuth(Role.ADMIN)
    public Result<PageResult<OrderVO>> getAllOrders(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String orderNo) {
        PageResult<OrderVO> pageResult = orderService.getOrderPage(current, size, userId, status, orderNo);
        return Result.success(pageResult);
    }

    @GetMapping("/{orderNo}")
    @RequireAuth(Role.ADMIN)
    public Result<OrderVO> getOrderByOrderNo(@PathVariable String orderNo) {
        OrderVO vo = orderService.getOrderVOByOrderNo(orderNo);
        return Result.success(vo);
    }

    @GetMapping("/user/{userId}")
    @RequireAuth(Role.ADMIN)
    public Result<List<OrderVO>> getOrdersByUserId(@PathVariable Long userId) {
        List<OrderVO> orderVOs = orderService.getOrderVOsByUserId(userId);
        return Result.success(orderVOs);
    }
}

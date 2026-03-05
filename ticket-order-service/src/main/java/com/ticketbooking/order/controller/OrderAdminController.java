package com.ticketbooking.order.controller;

import com.ticketbooking.common.annotation.RequireAdmin;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.service.OrderService;
import com.ticketbooking.order.model.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class OrderAdminController {
    
    private final OrderService orderService;
    
    @GetMapping("/{orderNo}")
    @RequireAdmin
    public Result<OrderVO> getOrderByOrderNo(@PathVariable String orderNo) {
        Order order = orderService.findByOrderNo(orderNo);
        return order != null ? Result.success(convertToVO(order)) : Result.error(3001, "订单不存在");
    }
    
    @GetMapping("/user/{userId}")
    @RequireAdmin
    public Result<List<OrderVO>> getOrdersByUserId(@PathVariable Long userId) {
        List<OrderVO> vos = orderService.findByUserId(userId).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(vos);
    }
    
    private OrderVO convertToVO(Order order) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        return vo;
    }
}

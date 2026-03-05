package com.ticketbooking.order.controller;

import com.ticketbooking.common.result.Result;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.service.OrderService;
import com.ticketbooking.order.model.qo.CreateOrderQO;
import com.ticketbooking.order.model.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {
    
    private final OrderService orderService;
    
    @GetMapping("/{orderNo}")
    public Result<OrderVO> findByOrderNo(@PathVariable String orderNo) {
        Order order = orderService.findByOrderNo(orderNo);
        return order != null ? Result.success(convertToVO(order)) : Result.success(null);
    }
    
    @PostMapping
    public Result<OrderVO> createOrder(@RequestBody CreateOrderQO qo) {
        Order order = orderService.createOrderFromStock(
                qo.getOrderNo(),
                qo.getUserId(),
                qo.getTicketId(),
                qo.getQuantity(),
                qo.getTotalPrice(),
                qo.getStatus()
        );
        return Result.success(convertToVO(order));
    }
    
    private OrderVO convertToVO(Order order) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        return vo;
    }
}

package com.ticketbooking.order.controller;

import com.ticketbooking.common.model.dto.OrderDTO;
import com.ticketbooking.common.model.qo.CreateOrderQO;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    @GetMapping("/{orderNo}")
    public OrderDTO findByOrderNo(@PathVariable String orderNo) {
        Order order = orderService.findByOrderNo(orderNo);
        return convertToDTO(order);
    }

    @PostMapping
    public OrderDTO createOrder(@RequestBody CreateOrderQO qo) {
        Order order = orderService.createOrderFromStock(
                qo.getOrderNo(),
                qo.getUserId(),
                qo.getConcertId(),
                qo.getGradeId(),
                qo.getQuantity(),
                qo.getTotalPrice(),
                qo.getStatus()
        );
        return convertToDTO(order);
    }

    private OrderDTO convertToDTO(Order order) {
        OrderDTO dto = new OrderDTO();
        BeanUtils.copyProperties(order, dto);
        return dto;
    }
}

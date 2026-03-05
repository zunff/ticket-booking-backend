package com.ticketbooking.order.controller;

import com.ticketbooking.common.annotation.RequireAuth;
import com.ticketbooking.common.context.UserContext;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.service.OrderService;
import com.ticketbooking.order.model.qo.BookTicketQO;
import com.ticketbooking.order.model.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    
    private final OrderService orderService;
    
    @PostMapping("/book")
    @RequireAuth
    public Result<String> bookTicket(@Valid @RequestBody BookTicketQO qo) {
        Long userId = UserContext.getUserId();
        String orderNo = orderService.createOrder(userId, qo.getTicketId(), qo.getQuantity());
        return Result.success("抢票成功，订单处理中", orderNo);
    }
    
    @GetMapping("/{orderNo}")
    @RequireAuth
    public Result<OrderVO> getOrderByOrderNo(@PathVariable String orderNo) {
        Order order = orderService.findByOrderNo(orderNo);
        return order != null ? Result.success(convertToVO(order)) : Result.error(3001, "订单不存在");
    }
    
    @GetMapping("/user/{userId}")
    @RequireAuth
    public Result<List<OrderVO>> getOrdersByUserId(@PathVariable Long userId) {
        List<Order> orders = orderService.findByUserId(userId);
        List<OrderVO> vos = orders.stream()
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

package com.ticketbooking.order.controller;

import com.ticketbooking.common.annotation.RequireAuth;
import com.ticketbooking.common.context.UserContext;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.order.converter.OrderConverter;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.service.OrderService;
import com.ticketbooking.order.model.qo.BookTicketQO;
import com.ticketbooking.order.model.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderConverter orderConverter;

    @PostMapping("/book")
    @RequireAuth
    public Result<String> bookTicket(@Valid @RequestBody BookTicketQO qo) {
        Long userId = UserContext.getUserId();
        String orderNo = orderService.createOrder(userId, qo.getConcertId(), qo.getGradeId(), qo.getQuantity());
        return Result.success("抢票成功，订单处理中", orderNo);
    }

    @GetMapping("/{orderNo}")
    @RequireAuth
    public Result<OrderVO> getOrderByOrderNo(@PathVariable String orderNo) {
        Order order = orderService.findByOrderNo(orderNo);
        return Result.success(orderConverter.toVO(order));
    }

    @GetMapping("/user/{userId}")
    @RequireAuth
    public Result<List<OrderVO>> getOrdersByUserId(@PathVariable Long userId) {
        List<Order> orders = orderService.findByUserId(userId);
        return Result.success(orderConverter.toVOList(orders));
    }
}

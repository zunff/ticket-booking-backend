package com.ticketbooking.order.controller;

import com.ticketbooking.common.annotation.RequireAuth;
import com.ticketbooking.common.annotation.UserRateLimit;
import com.ticketbooking.common.context.UserContext;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.order.model.qo.BookTicketQO;
import com.ticketbooking.order.model.vo.OrderVO;
import com.ticketbooking.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @UserRateLimit
    @PostMapping("/book")
    public Result<String> bookTicket(@Valid @RequestBody BookTicketQO qo) {
        Long userId = UserContext.getUserId();
        String orderNo = orderService.createOrder(userId, qo.getConcertId(), qo.getGradeId(), qo.getQuantity());
        return Result.success("抢票成功，订单处理中", orderNo);
    }

    @GetMapping("/{orderNo}")
    @RequireAuth
    public Result<OrderVO> getOrderByOrderNo(@PathVariable String orderNo) {
        OrderVO vo = orderService.getOrderVOByOrderNo(orderNo);
        return Result.success(vo);
    }

    @GetMapping("/user/{userId}")
    @RequireAuth
    public Result<PageResult<OrderVO>> getOrdersByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Integer status) {
        PageResult<OrderVO> pageResult = orderService.getOrderPageByUserId(userId, current, size, status);
        return Result.success(pageResult);
    }
}

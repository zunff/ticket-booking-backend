package com.ticketbooking.order.controller;

import com.ticketbooking.common.annotation.RequireAuth;
import com.ticketbooking.common.annotation.UserRateLimit;
import com.ticketbooking.common.context.UserContext;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.model.dto.PayResponseDTO;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.order.model.qo.BookTicketQO;
import com.ticketbooking.order.model.qo.InitiatePayQO;
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

    /**
     * 发起支付：校验订单后调用支付模块
     */
    @PostMapping("/{orderNo}/pay")
    @RequireAuth
    public Result<PayResponseDTO> initiatePayment(@PathVariable String orderNo,
                                                   @Valid @RequestBody InitiatePayQO qo) {
        Long userId = UserContext.getUserId();
        PayResponseDTO response = orderService.initiatePayment(userId, orderNo, qo);
        return Result.success(response);
    }

    /**
     * 取消订单并退款（管理员）
     */
    @PostMapping("/{orderNo}/refund")
    @RequireAuth
    public Result<Void> refundOrder(@PathVariable String orderNo) {
        orderService.cancelAndRefund(orderNo);
        return Result.success();
    }
}

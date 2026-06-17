package com.ticketbooking.payment.controller;

import com.ticketbooking.common.annotation.RequireAuth;
import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.enums.Role;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.payment.model.vo.PaymentRecordVO;
import com.ticketbooking.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class PaymentAdminController {

    private final PaymentService paymentService;

    @GetMapping
    @RequireAuth(Role.ADMIN)
    public Result<PageResult<PaymentRecordVO>> list(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String outTradeNo,
            @RequestParam(required = false) PayChannel channel,
            @RequestParam(required = false) Integer status) {
        return Result.success(paymentService.getPage(current, size, outTradeNo, channel, status));
    }

    @GetMapping("/{outTradeNo}")
    @RequireAuth(Role.ADMIN)
    public Result<PaymentRecordVO> getByOutTradeNo(@PathVariable String outTradeNo) {
        return Result.success(paymentService.getDetail(outTradeNo));
    }
}

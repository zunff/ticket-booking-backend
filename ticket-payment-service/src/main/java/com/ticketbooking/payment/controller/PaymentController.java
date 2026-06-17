package com.ticketbooking.payment.controller;

import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.payment.converter.PaymentConverter;
import com.ticketbooking.payment.model.qo.PayRequestQO;
import com.ticketbooking.payment.model.qo.RefundRequestQO;
import com.ticketbooking.payment.model.vo.PaymentRecordVO;
import com.ticketbooking.payment.model.vo.PaymentVO;
import com.ticketbooking.payment.model.vo.RefundVO;
import com.ticketbooking.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentConverter converter;

    @PostMapping("/prepay")
    public Result<PaymentVO> prepay(@Valid @RequestBody PayRequestQO request) {
        return Result.success(converter.toVO(paymentService.prepay(request)));
    }

    @GetMapping("/query/{outTradeNo}")
    public Result<PaymentRecordVO> query(@PathVariable String outTradeNo,
                                         @RequestParam PayChannel channel) {
        paymentService.query(outTradeNo, channel);
        return Result.success(paymentService.getDetail(outTradeNo));
    }

    @PostMapping("/close/{outTradeNo}")
    public Result<Boolean> close(@PathVariable String outTradeNo,
                                 @RequestParam PayChannel channel) {
        return Result.success(paymentService.close(outTradeNo, channel));
    }

    @PostMapping("/refund")
    public Result<RefundVO> refund(@Valid @RequestBody RefundRequestQO request) {
        return Result.success(converter.toVO(paymentService.refund(request)));
    }
}

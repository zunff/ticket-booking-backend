package com.ticketbooking.payment.controller;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.model.dto.PayResponseDTO;
import com.ticketbooking.common.model.dto.RefundResultDTO;
import com.ticketbooking.common.model.dto.TradeQueryDTO;
import com.ticketbooking.common.model.qo.PayRequestQO;
import com.ticketbooking.common.model.qo.RefundRequestQO;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 支付内部接口 — 供其他微服务（order-service）通过 Feign 调用。
 * 路径约定：/{module}/internal/**，返回 Result<T>，由 FeignResultDecoder 自动拆包。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalPaymentController {

    private final PaymentService paymentService;

    @PostMapping("/prepay")
    public Result<PayResponseDTO> prepay(@RequestBody PayRequestQO request) {
        return Result.success(paymentService.prepay(request));
    }

    @GetMapping("/query/{orderNo}")
    public Result<TradeQueryDTO> query(@PathVariable String orderNo,
                                        @RequestParam String channel) {
        return Result.success(paymentService.query(orderNo, toChannel(channel)));
    }

    @PostMapping("/close/{orderNo}")
    public Result<Boolean> close(@PathVariable String orderNo,
                                  @RequestParam String channel) {
        return Result.success(paymentService.close(orderNo, toChannel(channel)));
    }

    @PostMapping("/refund")
    public Result<RefundResultDTO> refund(@RequestBody RefundRequestQO request) {
        return Result.success(paymentService.refund(request));
    }

    private PayChannel toChannel(String channel) {
        PayChannel payChannel = PayChannel.of(channel);
        if (payChannel == null) {
            throw new BusinessException(ErrorCode.PAYMENT_CHANNEL_NOT_SUPPORTED);
        }
        return payChannel;
    }
}

package com.ticketbooking.common.model.qo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class PayRequestQO {

    /** 业务订单号（调用方传入，一个 orderNo 可对应多条支付流水） */
    @NotBlank
    private String orderNo;

    /** 发给渠道的商户单号：由 payment 服务生成（= paymentNo），调用方无需填；下单链路内部传递给渠道/Handler */
    private String outTradeNo;

    @NotNull
    @Min(1)
    private Integer amount;

    @NotBlank
    private String subject;

    private String description;

    @NotNull
    private String channel;

    private String payMode;

    private String openId;

    private String returnUrl;

    private Map<String, String> extras;
}

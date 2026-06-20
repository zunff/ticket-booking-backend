package com.ticketbooking.common.model.qo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class RefundRequestQO {

    /** 业务订单号（调用方传入），payment 服务据此解析到具体支付流水 */
    @NotBlank
    private String orderNo;

    /** 发给渠道的商户单号：由 payment 服务解析回填，调用方无需填 */
    private String outTradeNo;

    @NotBlank
    private String refundNo;

    @NotNull
    @Min(1)
    private Integer refundAmount;

    private Integer totalAmount;

    private String reason;

    @NotNull
    private String channel;

    private Map<String, String> extras;
}

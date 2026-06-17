package com.ticketbooking.payment.model.qo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class RefundRequestQO {

    @NotBlank
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

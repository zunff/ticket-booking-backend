package com.ticketbooking.payment.model.qo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class PayRequestQO {

    @NotBlank
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

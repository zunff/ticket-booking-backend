package com.ticketbooking.payment.model.vo;

import lombok.Data;

import java.util.Map;

@Data
public class PaymentVO {

    private String paymentNo;

    private String channelTradeNo;

    private String payMode;

    private String payUrl;

    private Map<String, String> payParams;
}

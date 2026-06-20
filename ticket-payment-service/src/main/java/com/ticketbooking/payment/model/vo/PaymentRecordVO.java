package com.ticketbooking.payment.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentRecordVO {

    private Long id;
    private String paymentNo;
    private String orderNo;
    private String outTradeNo;
    private String channel;
    private String payMode;
    private Integer amount;
    private Integer paidAmount;
    private String status;
    private String channelTradeNo;
    private String subject;
    private LocalDateTime payTime;
    private LocalDateTime createTime;
}

package com.ticketbooking.payment.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RefundVO {

    private boolean success;

    private String refundNo;

    private String channelRefundNo;

    private Integer refundAmount;

    private LocalDateTime refundTime;
}

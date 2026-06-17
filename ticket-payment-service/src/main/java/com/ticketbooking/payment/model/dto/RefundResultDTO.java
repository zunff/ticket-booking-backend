package com.ticketbooking.payment.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RefundResultDTO {

    private boolean success;

    private String refundNo;

    private String channelRefundNo;

    private Integer refundAmount;

    private LocalDateTime refundTime;
}

package com.ticketbooking.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResultDTO {

    private boolean success;

    private String refundNo;

    private String channelRefundNo;

    private Integer refundAmount;

    private LocalDateTime refundTime;
}

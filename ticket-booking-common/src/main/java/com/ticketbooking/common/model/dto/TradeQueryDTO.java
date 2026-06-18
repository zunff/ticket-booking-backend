package com.ticketbooking.common.model.dto;

import com.ticketbooking.common.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TradeQueryDTO {

    private PaymentStatus status;

    private String channelTradeNo;

    private Integer paidAmount;

    private LocalDateTime payTime;
}

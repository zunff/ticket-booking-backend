package com.ticketbooking.common.model.dto;

import com.ticketbooking.common.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeQueryDTO {

    private PaymentStatus status;

    private String channelTradeNo;

    private Integer paidAmount;

    private LocalDateTime payTime;
}

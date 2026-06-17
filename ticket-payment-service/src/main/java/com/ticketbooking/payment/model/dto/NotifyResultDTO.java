package com.ticketbooking.payment.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotifyResultDTO {

    private boolean success;

    private String outTradeNo;

    private String channelTradeNo;

    private Integer paidAmount;

    private LocalDateTime payTime;
}

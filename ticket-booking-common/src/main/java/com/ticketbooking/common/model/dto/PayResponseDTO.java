package com.ticketbooking.common.model.dto;

import com.ticketbooking.common.enums.PayMode;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class PayResponseDTO {

    private String paymentNo;

    private String channelTradeNo;

    private PayMode payMode;

    private String payUrl;

    private Map<String, String> payParams;
}

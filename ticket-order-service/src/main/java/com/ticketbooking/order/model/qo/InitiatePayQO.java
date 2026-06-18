package com.ticketbooking.order.model.qo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InitiatePayQO {

    @NotBlank
    private String channel;

    private String payMode;

    private String openId;

    private String returnUrl;
}

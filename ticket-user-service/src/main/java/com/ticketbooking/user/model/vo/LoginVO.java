package com.ticketbooking.user.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {
    private UserVO user;
    private String token;
}

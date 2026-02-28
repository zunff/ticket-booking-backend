package com.ticketbooking.user.dto;

import com.ticketbooking.user.entity.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private User user;
    private String token;
}

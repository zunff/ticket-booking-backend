package com.ticketbooking.user.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "登录响应")
public class LoginVO {

    @Schema(description = "用户信息")
    private UserVO user;

    @Schema(description = "JWT令牌")
    private String token;
}

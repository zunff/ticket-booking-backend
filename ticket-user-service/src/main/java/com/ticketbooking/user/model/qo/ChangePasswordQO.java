package com.ticketbooking.user.model.qo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "修改密码请求")
public class ChangePasswordQO {

    @NotBlank(message = "当前密码不能为空")
    @Schema(description = "当前密码", required = true)
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20位之间")
    @Schema(description = "新密码", required = true)
    private String newPassword;
}

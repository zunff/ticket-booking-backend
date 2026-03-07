package com.ticketbooking.user.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private Integer status;
    private Boolean isAdmin;
    private LocalDateTime createTime;
}

package com.ticketbooking.order.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OrderVO {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long concertId;
    private String concertName;
    private Long gradeId;
    private String gradeName;
    private Integer quantity;
    private Integer totalPrice;
    private Integer status;
    private LocalDateTime createTime;
}

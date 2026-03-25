package com.ticketbooking.order.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    /**
     * 失败原因（当 status=FAILED 时显示）
     */
    private String failReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;
}

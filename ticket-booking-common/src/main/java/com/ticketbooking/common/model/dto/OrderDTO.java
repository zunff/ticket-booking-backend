package com.ticketbooking.common.model.dto;

import lombok.Data;

/**
 * 订单DTO - 跨服务共用
 */
@Data
public class OrderDTO {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long concertId;
    private Long gradeId;
    private Integer quantity;
    private Integer totalPrice;
    private Integer status;
    /**
     * 失败原因（当 status=FAILED 时显示）
     */
    private String failReason;
}

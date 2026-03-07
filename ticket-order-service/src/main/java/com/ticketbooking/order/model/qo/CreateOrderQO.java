package com.ticketbooking.order.model.qo;

import lombok.Data;

@Data
public class CreateOrderQO {
    private String orderNo;
    private Long userId;
    private Long concertId;
    private Long gradeId;
    private Integer quantity;
    private Integer totalPrice;
    private Integer status;
}

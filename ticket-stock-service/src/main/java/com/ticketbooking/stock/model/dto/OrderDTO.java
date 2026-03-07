package com.ticketbooking.stock.model.dto;

import lombok.Data;

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
}

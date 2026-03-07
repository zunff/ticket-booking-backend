package com.ticketbooking.stock.model.qo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderQO {
    private String orderNo;
    private Long userId;
    private Long concertId;
    private Long gradeId;
    private Integer quantity;
    private Integer totalPrice;
    private Integer status;
}

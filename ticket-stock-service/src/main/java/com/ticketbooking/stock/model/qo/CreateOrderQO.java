package com.ticketbooking.stock.model.qo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderQO {
    private String orderNo;
    private Long userId;
    private Long ticketId;
    private Integer quantity;
    private BigDecimal totalPrice;
    private String status;
}

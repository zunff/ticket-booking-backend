package com.ticketbooking.order.model.qo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateOrderQO {
    private String orderNo;
    private Long userId;
    private Long ticketId;
    private Integer quantity;
    private BigDecimal totalPrice;
    private String status;
}

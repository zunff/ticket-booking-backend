package com.ticketbooking.stock.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderDTO {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long ticketId;
    private Integer quantity;
    private BigDecimal totalPrice;
    private String status;
}

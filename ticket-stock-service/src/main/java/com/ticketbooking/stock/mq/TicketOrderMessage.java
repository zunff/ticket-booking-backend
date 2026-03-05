package com.ticketbooking.stock.mq;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class TicketOrderMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String orderNo;
    private Long userId;
    private Long ticketId;
    private Integer quantity;
    private BigDecimal totalPrice;
    private Long timestamp;
    
    public TicketOrderMessage(String orderNo, Long userId, Long ticketId, Integer quantity, BigDecimal totalPrice) {
        this.orderNo = orderNo;
        this.userId = userId;
        this.ticketId = ticketId;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.timestamp = System.currentTimeMillis();
    }
}

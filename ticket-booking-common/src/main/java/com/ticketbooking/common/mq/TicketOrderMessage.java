package com.ticketbooking.common.mq;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class TicketOrderMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String orderNo;
    private Long userId;
    private Long concertId;
    private Long gradeId;
    private Integer quantity;
    private Integer totalPrice;
    private Long timestamp;
    
    public TicketOrderMessage(String orderNo, Long userId, Long concertId, Long gradeId, Integer quantity, Integer totalPrice) {
        this.orderNo = orderNo;
        this.userId = userId;
        this.concertId = concertId;
        this.gradeId = gradeId;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.timestamp = System.currentTimeMillis();
    }
}

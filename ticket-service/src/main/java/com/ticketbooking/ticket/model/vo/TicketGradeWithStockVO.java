package com.ticketbooking.ticket.model.vo;

import lombok.Data;

@Data
public class TicketGradeWithStockVO {
    private Long id;
    private Long concertId;
    private String gradeName;
    private Integer price;
    private Integer totalStock;
    private Integer isSelectedSeat;
    private Integer availableStock;
}

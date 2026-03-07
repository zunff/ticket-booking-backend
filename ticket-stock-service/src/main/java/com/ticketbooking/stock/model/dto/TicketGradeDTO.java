package com.ticketbooking.stock.model.dto;

import lombok.Data;

@Data
public class TicketGradeDTO {
    private Long id;
    private Long concertId;
    private String gradeName;
    private Integer price;
    private Integer totalStock;
    private Integer isSelectedSeat;
}

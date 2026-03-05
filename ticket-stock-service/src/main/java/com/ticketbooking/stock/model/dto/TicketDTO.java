package com.ticketbooking.stock.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class TicketDTO {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer totalStock;
    private Integer availableStock;
    private String status;
}

package com.ticketbooking.order.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketInfoDTO {
    
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer availableStock;
    private String status;
}

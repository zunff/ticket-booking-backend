package com.ticketbooking.ticket.model.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TicketVO {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer totalStock;
    private Integer availableStock;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
}

package com.ticketbooking.ticket.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ConcertDetailWithStockVO {
    private Long id;
    private String name;
    private String venue;
    private LocalDateTime showTime;
    private LocalDateTime startSaleTime;
    private LocalDateTime endSaleTime;
    private Integer status;
    private LocalDateTime createdAt;
    private List<TicketGradeWithStockVO> grades;
}

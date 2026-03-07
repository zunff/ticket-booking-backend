package com.ticketbooking.ticket.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConcertVO {
    private Long id;
    private String name;
    private String venue;
    private LocalDateTime showTime;
    private LocalDateTime startSaleTime;
    private LocalDateTime endSaleTime;
    private Integer status;
    private LocalDateTime createdAt;
}

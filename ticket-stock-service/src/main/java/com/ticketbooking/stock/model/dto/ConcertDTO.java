package com.ticketbooking.stock.model.dto;

import lombok.Data;

@Data
public class ConcertDTO {
    private Long id;
    private String name;
    private String venue;
    private String showTime;
    private String startSaleTime;
    private String endSaleTime;
    private Integer status;
}

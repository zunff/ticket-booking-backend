package com.ticketbooking.common.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 演唱会DTO - 跨服务共用
 */
@Data
public class ConcertDTO {
    private Long id;
    private String name;
    private String venue;
    private LocalDateTime showTime;
    private LocalDateTime startSaleTime;
    private LocalDateTime endSaleTime;
    private Integer purchaseLimit;
    private Integer status;
}

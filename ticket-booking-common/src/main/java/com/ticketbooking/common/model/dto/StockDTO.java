package com.ticketbooking.common.model.dto;

import lombok.Data;

/**
 * 库存DTO - 跨服务共用
 */
@Data
public class StockDTO {
    private Long id;
    private Long concertId;
    private String concertName;
    private Long gradeId;
    private String gradeName;
    private Integer price;
    private Integer availableStock;
}

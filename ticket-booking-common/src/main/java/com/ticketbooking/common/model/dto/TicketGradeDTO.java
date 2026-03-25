package com.ticketbooking.common.model.dto;

import lombok.Data;

/**
 * 票档DTO - 跨服务共用
 */
@Data
public class TicketGradeDTO {
    private Long id;
    private Long concertId;
    private String concertName;
    private String gradeName;
    private Integer price;
    private Integer totalStock;
    private Integer isSelectedSeat;
    /**
     * 演唱会限购数量
     */
    private Integer purchaseLimit;
}

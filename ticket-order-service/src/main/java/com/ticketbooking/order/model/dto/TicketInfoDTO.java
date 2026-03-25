package com.ticketbooking.order.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketInfoDTO {

    private Long concertId;
    private Long gradeId;
    private String concertName;
    private String gradeName;
    private Integer price;
    private Integer availableStock;
    /**
     * 演唱会限购数量
     */
    private Integer purchaseLimit;
}

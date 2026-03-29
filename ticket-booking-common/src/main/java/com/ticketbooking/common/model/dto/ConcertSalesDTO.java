package com.ticketbooking.common.model.dto;

import lombok.Data;

/**
 * 演唱会销售统计 DTO（用于服务间传输）
 */
@Data
public class ConcertSalesDTO {
    /**
     * 演唱会ID
     */
    private Long concertId;

    /**
     * 订单总数
     */
    private Integer totalOrders;

    /**
     * 票数总和
     */
    private Integer totalTickets;

    /**
     * 总收入（分为单位）
     */
    private Long totalRevenue;
}

package com.ticketbooking.ticket.model.vo;

import lombok.Data;

/**
 * 演唱会销售统计
 */
@Data
public class ConcertSalesStatsVO {
    /**
     * 演唱会ID
     */
    private Long concertId;

    /**
     * 演唱会名称
     */
    private String concertName;

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

    /**
     * 完成率（已售/总库存）
     */
    private Double completionRate;
}

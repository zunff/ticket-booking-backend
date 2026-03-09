package com.ticketbooking.ticket.model.vo;

import lombok.Data;

/**
 * 仪表盘统计数据
 */
@Data
public class DashboardStatsVO {
    /**
     * 演唱会总数
     */
    private Integer totalConcerts;

    /**
     * 在售演唱会数量
     */
    private Integer onSaleConcerts;

    /**
     * 订单总数
     */
    private Integer totalOrders;

    /**
     * 总收入（分为单位）
     */
    private Long totalRevenue;

    /**
     * 今日订单数
     */
    private Integer todayOrders;

    /**
     * 今日收入（分为单位）
     */
    private Long todayRevenue;
}

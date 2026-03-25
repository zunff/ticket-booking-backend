package com.ticketbooking.common.model.dto;

import lombok.Data;

/**
 * 仪表盘统计数据 DTO（用于服务间传输）
 */
@Data
public class DashboardStatsDTO {
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

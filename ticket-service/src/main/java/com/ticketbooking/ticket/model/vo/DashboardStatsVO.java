package com.ticketbooking.ticket.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "仪表盘统计数据")
public class DashboardStatsVO {

    @Schema(description = "演唱会总数")
    private Integer totalConcerts;

    @Schema(description = "在售演唱会数量")
    private Integer onSaleConcerts;

    @Schema(description = "订单总数")
    private Integer totalOrders;

    @Schema(description = "总收入（分）")
    private Long totalRevenue;

    @Schema(description = "今日订单数")
    private Integer todayOrders;

    @Schema(description = "今日收入（分）")
    private Long todayRevenue;
}

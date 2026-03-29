package com.ticketbooking.ticket.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "演唱会销售统计")
public class ConcertSalesStatsVO {

    @Schema(description = "演唱会ID")
    private Long concertId;

    @Schema(description = "演唱会名称")
    private String concertName;

    @Schema(description = "订单总数")
    private Integer totalOrders;

    @Schema(description = "已售票数")
    private Integer totalTickets;

    @Schema(description = "总收入（分）")
    private Long totalRevenue;

    @Schema(description = "完成率（已售/总库存）")
    private Double completionRate;
}

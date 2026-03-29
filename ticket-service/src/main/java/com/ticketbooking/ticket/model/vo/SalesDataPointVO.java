package com.ticketbooking.ticket.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "销售数据点（用于图表）")
public class SalesDataPointVO {

    @Schema(description = "日期（格式：yyyy-MM-dd）")
    private String date;

    @Schema(description = "订单数")
    private Integer orders;

    @Schema(description = "收入（分）")
    private Long revenue;
}

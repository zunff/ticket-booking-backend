package com.ticketbooking.ticket.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "票档信息（含库存）")
public class TicketGradeWithStockVO {

    @Schema(description = "票档ID")
    private Long id;

    @Schema(description = "演唱会ID")
    private Long concertId;

    @Schema(description = "票档名称")
    private String gradeName;

    @Schema(description = "票价（分）")
    private Integer price;

    @Schema(description = "总库存")
    private Integer totalStock;

    @Schema(description = "是否选座：0-否，1-是")
    private Integer isSelectedSeat;

    @Schema(description = "可用库存")
    private Integer availableStock;
}

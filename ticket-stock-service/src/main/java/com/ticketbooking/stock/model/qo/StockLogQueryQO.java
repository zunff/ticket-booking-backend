package com.ticketbooking.stock.model.qo;

import com.ticketbooking.common.model.qo.PageQO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "库存日志查询")
public class StockLogQueryQO extends PageQO {

    @Schema(description = "演唱会ID")
    private Long concertId;

    @Schema(description = "票档ID")
    private Long gradeId;
}

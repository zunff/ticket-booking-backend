package com.ticketbooking.order.model.qo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "抢票请求")
public class BookTicketQO {

    @Schema(description = "演唱会ID")
    @NotNull(message = "演唱会ID不能为空")
    private Long concertId;

    @Schema(description = "票档ID")
    @NotNull(message = "档位ID不能为空")
    private Long gradeId;

    @Schema(description = "购买数量", defaultValue = "1")
    @Min(value = 1, message = "购买数量至少为1")
    private Integer quantity = 1;
}

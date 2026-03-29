package com.ticketbooking.common.model.qo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建订单请求（跨服务共用）")
public class CreateOrderQO {

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "演唱会ID")
    private Long concertId;

    @Schema(description = "票档ID")
    private Long gradeId;

    @Schema(description = "购买数量")
    private Integer quantity;

    @Schema(description = "订单总金额（分）")
    private Integer totalPrice;

    @Schema(description = "订单状态")
    private Integer status;

    @Schema(description = "失败原因")
    private String failReason;
}

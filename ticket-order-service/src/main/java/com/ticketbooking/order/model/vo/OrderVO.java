package com.ticketbooking.order.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "订单信息")
public class OrderVO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "演唱会ID")
    private Long concertId;

    @Schema(description = "演唱会名称")
    private String concertName;

    @Schema(description = "票档ID")
    private Long gradeId;

    @Schema(description = "票档名称")
    private String gradeName;

    @Schema(description = "购买数量")
    private Integer quantity;

    @Schema(description = "订单总金额（分）")
    private Integer totalPrice;

    @Schema(description = "订单状态：0-处理中 1-待支付 2-已支付 3-已取消 4-失败")
    private Integer status;

    @Schema(description = "失败原因（状态为失败时显示）")
    private String failReason;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;
}

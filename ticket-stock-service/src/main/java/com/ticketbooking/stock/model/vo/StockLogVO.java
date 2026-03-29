package com.ticketbooking.stock.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "库存日志")
public class StockLogVO {

    @Schema(description = "日志ID")
    private Long id;

    @Schema(description = "演唱会ID")
    private Long concertId;

    @Schema(description = "演唱会名称")
    private String concertName;

    @Schema(description = "票档ID")
    private Long gradeId;

    @Schema(description = "票档名称")
    private String gradeName;

    @Schema(description = "变更数量")
    private Integer changeQuantity;

    @Schema(description = "变更前库存")
    private Integer beforeStock;

    @Schema(description = "变更后库存")
    private Integer afterStock;

    @Schema(description = "操作类型")
    private String operationType;

    @Schema(description = "操作人")
    private String operator;

    @Schema(description = "变更原因")
    private String reason;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;
}

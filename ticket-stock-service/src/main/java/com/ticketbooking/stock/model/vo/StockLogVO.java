package com.ticketbooking.stock.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存日志VO
 */
@Data
public class StockLogVO {
    private Long id;
    private Long concertId;
    private String concertName;
    private Long gradeId;
    private String gradeName;
    private Integer changeQuantity;
    private Integer beforeStock;
    private Integer afterStock;
    private String operationType;
    private String operator;
    private String reason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;
}

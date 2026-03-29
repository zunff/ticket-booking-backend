package com.ticketbooking.ticket.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "演唱会信息")
public class ConcertVO {

    @Schema(description = "演唱会ID")
    private Long id;

    @Schema(description = "演唱会名称")
    private String name;

    @Schema(description = "演出场地")
    private String venue;

    @Schema(description = "演出时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime showTime;

    @Schema(description = "开售时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime startSaleTime;

    @Schema(description = "结束售票时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime endSaleTime;

    @Schema(description = "状态：0-已关闭，1-正常")
    private Integer status;

    @Schema(description = "状态描述（根据时间动态计算）")
    private String statusText;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;
}

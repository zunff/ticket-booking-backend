package com.ticketbooking.ticket.model.qo;

import com.ticketbooking.common.model.qo.PageQO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "演唱会分页查询")
public class ConcertQueryQO extends PageQO {

    @Schema(description = "演唱会名称（模糊查询）")
    private String name;

    @Schema(description = "动态状态筛选：0-已关闭，1-开售中，2-即将开售，3-已结束，null-显示所有非关闭")
    private Integer timeStatus;
}

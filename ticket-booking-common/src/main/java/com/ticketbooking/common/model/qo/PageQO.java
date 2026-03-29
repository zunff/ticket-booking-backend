package com.ticketbooking.common.model.qo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "通用分页查询")
public class PageQO {

    @Schema(description = "当前页码", defaultValue = "1")
    private Long current = 1L;

    @Schema(description = "每页大小", defaultValue = "10")
    private Long size = 10L;
}

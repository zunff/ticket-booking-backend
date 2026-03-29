package com.ticketbooking.ticket.model.qo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "更新演唱会请求")
public class ConcertUpdateQO {

    @Schema(description = "演唱会名称")
    private String name;

    @Schema(description = "演出场地")
    private String venue;

    @Schema(description = "演出时间")
    private LocalDateTime showTime;

    @Schema(description = "开售时间")
    private LocalDateTime startSaleTime;

    @Schema(description = "结束售票时间")
    private LocalDateTime endSaleTime;

    @Schema(description = "每人限购数量")
    private Integer purchaseLimit;

    @Schema(description = "状态：0-已关闭，1-正常")
    private Integer status;

    @Schema(description = "票档列表（传入则全量更新）")
    private List<TicketGradeQO> grades;

    @Data
    @Schema(description = "票档信息")
    public static class TicketGradeQO {
        @Schema(description = "票档ID（更新时传入，新增时不传）")
        private Long id;

        @Schema(description = "票档名称")
        private String gradeName;

        @Schema(description = "票价（分）")
        private Integer price;

        @Schema(description = "总库存")
        private Integer totalStock;

        @Schema(description = "是否选座：0-否，1-是")
        private Integer isSelectedSeat;
    }
}

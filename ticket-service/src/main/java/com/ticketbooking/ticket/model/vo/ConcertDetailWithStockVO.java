package com.ticketbooking.ticket.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ConcertDetailWithStockVO {
    private Long id;
    private String name;
    private String venue;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime showTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime startSaleTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime endSaleTime;

    private Integer status;
    /**
     * 状态描述（根据时间动态计算）
     */
    private String statusText;

    /**
     * 每人限购数量
     */
    private Integer purchaseLimit;

    /**
     * 当前用户已购买数量（需要登录后才有值）
     */
    private Integer userPurchasedCount;

    /**
     * 当前用户是否可以继续购买
     */
    private Boolean canPurchase;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    private List<TicketGradeWithStockVO> grades;
}

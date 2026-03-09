package com.ticketbooking.ticket.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConcertVO {
    private Long id;
    private String name;
    private String venue;
    private LocalDateTime showTime;
    private LocalDateTime startSaleTime;
    private LocalDateTime endSaleTime;
    private Integer status;
    /**
     * 状态描述（根据时间动态计算）
     */
    private String statusText;
    private LocalDateTime createdAt;
}

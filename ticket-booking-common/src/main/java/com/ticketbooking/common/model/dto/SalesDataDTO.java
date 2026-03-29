package com.ticketbooking.common.model.dto;

import lombok.Data;

/**
 * 销售数据点 DTO（用于服务间传输）
 */
@Data
public class SalesDataDTO {
    /**
     * 日期（格式：yyyy-MM-dd）
     */
    private String date;

    /**
     * 订单数
     */
    private Integer orders;

    /**
     * 收入（分为单位）
     */
    private Long revenue;
}

package com.ticketbooking.ticket.model.vo;

import lombok.Data;

/**
 * 销售数据点（用于图表）
 */
@Data
public class SalesDataPointVO {
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

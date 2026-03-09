package com.ticketbooking.common.model.qo;

import lombok.Data;

/**
 * 通用分页查询QO
 * 所有分页查询的QO都应继承此类
 */
@Data
public class PageQO {

    /**
     * 当前页码，默认第1页
     */
    private Long current = 1L;

    /**
     * 每页大小，默认10条
     */
    private Long size = 10L;
}

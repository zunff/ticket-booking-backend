package com.ticketbooking.ticket.model.qo;

import com.ticketbooking.common.model.qo.PageQO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 演唱会分页查询QO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConcertQueryQO extends PageQO {

    /**
     * 演唱会名称（模糊查询）
     */
    private String name;

    /**
     * 状态（1-未上架，2-上架中）
     */
    private Integer status;
}

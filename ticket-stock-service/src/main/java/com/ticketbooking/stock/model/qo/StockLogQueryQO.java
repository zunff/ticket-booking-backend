package com.ticketbooking.stock.model.qo;

import com.ticketbooking.common.model.qo.PageQO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库存日志查询QO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StockLogQueryQO extends PageQO {

    /**
     * 演唱会ID
     */
    private Long concertId;

    /**
     * 票档ID
     */
    private Long gradeId;
}

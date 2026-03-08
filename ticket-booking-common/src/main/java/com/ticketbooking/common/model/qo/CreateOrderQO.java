package com.ticketbooking.common.model.qo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建订单QO - 跨服务共用
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderQO {
    private String orderNo;
    private Long userId;
    private Long concertId;
    private Long gradeId;
    private Integer quantity;
    private Integer totalPrice;
    private Integer status;
}

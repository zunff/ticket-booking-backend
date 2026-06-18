package com.ticketbooking.payment.strategy;

import com.ticketbooking.common.model.qo.RefundRequestQO;
import com.ticketbooking.common.model.dto.RefundResultDTO;

/**
 * 可退款能力。继承基接口 {@link PayChannelStrategy}。
 * <p>
 * 渠道按需实现本接口。
 */
public interface RefundCapable extends PayChannelStrategy {

    /**
     * 发起退款
     */
    RefundResultDTO refund(RefundRequestQO request);
}

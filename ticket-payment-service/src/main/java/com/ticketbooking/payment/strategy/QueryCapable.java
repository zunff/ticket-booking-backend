package com.ticketbooking.payment.strategy;

import com.ticketbooking.common.model.dto.TradeQueryDTO;

/**
 * 可查询订单能力。继承基接口 {@link PayChannelStrategy}。
 * <p>
 * 渠道按需实现本接口。不支持主动查询的渠道（部分聚合支付）无需实现，
 * 调用方通过 instanceof 探测，未实现时返回 PAYMENT_CAPABILITY_NOT_SUPPORTED。
 */
public interface QueryCapable extends PayChannelStrategy {

    /**
     * 主动查询订单支付状态
     *
     * @param outTradeNo 业务订单号
     */
    TradeQueryDTO query(String outTradeNo);
}

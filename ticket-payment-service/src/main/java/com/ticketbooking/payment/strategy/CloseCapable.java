package com.ticketbooking.payment.strategy;

/**
 * 可关闭订单能力。继承基接口 {@link PayChannelStrategy}。
 * <p>
 * 渠道按需实现本接口。
 */
public interface CloseCapable extends PayChannelStrategy {

    /**
     * 关闭未支付的订单，阻止用户继续支付
     *
     * @param outTradeNo 业务订单号
     * @return 是否关闭成功
     */
    boolean close(String outTradeNo);
}

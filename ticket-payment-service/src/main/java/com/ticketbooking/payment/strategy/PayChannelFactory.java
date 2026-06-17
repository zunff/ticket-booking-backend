package com.ticketbooking.payment.strategy;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 支付渠道策略自注册工厂。
 * <p>
 * Spring 自动注入所有 {@link PayChannelStrategy} 实现，按 {@link PayChannelStrategy#channel()}
 * 枚举值建立映射。新增渠道只需新增一个 Strategy Bean，工厂自动收集，零配置。
 */
@Component
public class PayChannelFactory {

    private final Map<PayChannel, PayChannelStrategy> strategyMap;

    public PayChannelFactory(List<PayChannelStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(PayChannelStrategy::channel, Function.identity()));
    }

    /**
     * 获取指定渠道的策略实例
     *
     * @throws BusinessException PAYMENT_CHANNEL_NOT_SUPPORTED 渠道未注册
     */
    public PayChannelStrategy get(PayChannel channel) {
        PayChannelStrategy strategy = strategyMap.get(channel);
        if (strategy == null) {
            throw new BusinessException(ErrorCode.PAYMENT_CHANNEL_NOT_SUPPORTED);
        }
        return strategy;
    }
}

package com.ticketbooking.payment.strategy;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.enums.PayMode;
import com.ticketbooking.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付方式处理器自注册工厂。
 * <p>
 * Spring 自动注入所有 {@link PayModeHandler} 实现，按 (channel, payMode) 建二级映射。
 * 新增 handler 只需新增一个 Bean，工厂自动收集，零配置。
 */
@Component
public class PayModeHandlerFactory {

    private final Map<PayChannel, Map<PayMode, PayModeHandler>> handlerMap;

    public PayModeHandlerFactory(List<PayModeHandler> handlers) {
        this.handlerMap = new ConcurrentHashMap<>();
        for (PayModeHandler handler : handlers) {
            handlerMap
                    .computeIfAbsent(handler.channel(), k -> new ConcurrentHashMap<>())
                    .put(handler.payMode(), handler);
        }
    }

    /**
     * 获取指定渠道 + 支付方式的处理器
     *
     * @throws BusinessException PAYMENT_CAPABILITY_NOT_SUPPORTED 渠道不支持该支付方式
     */
    public PayModeHandler get(PayChannel channel, PayMode payMode) {
        Map<PayMode, PayModeHandler> channelHandlers = handlerMap.get(channel);
        if (channelHandlers == null) {
            throw new BusinessException(ErrorCode.PAYMENT_CAPABILITY_NOT_SUPPORTED);
        }
        PayModeHandler handler = channelHandlers.get(payMode);
        if (handler == null) {
            throw new BusinessException(ErrorCode.PAYMENT_CAPABILITY_NOT_SUPPORTED, channel.getDesc() + "不支持支付方式: " + payMode.getDesc());
        }
        return handler;
    }
}

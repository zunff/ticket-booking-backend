package com.ticketbooking.payment.strategy;

import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.enums.PayMode;
import com.ticketbooking.payment.model.dto.PayResponseDTO;
import com.ticketbooking.payment.model.qo.PayRequestQO;

/**
 * 支付方式处理器 — 策略内的策略。
 * <p>
 * 每种 (渠道, 支付方式) 组合一个 Bean（如 WechatNativeHandler / AlipayWapHandler），
 * 由 {@link PayModeHandlerFactory} 按 {@link #channel()} + {@link #payMode()} 自动注册。
 * 新增支付方式 = 加一个实现类，工厂自动收集，{@link PayChannelStrategy} 主干不动。
 * <p>
 * 只负责"调渠道下单 API"，返回 payUrl / payParams；幂等、锁、记录管理仍由
 * {@link AbstractPayChannelStrategy} 模板方法处理。
 */
public interface PayModeHandler {

    PayChannel channel();

    PayMode payMode();

    PayResponseDTO prepay(PayRequestQO request);
}

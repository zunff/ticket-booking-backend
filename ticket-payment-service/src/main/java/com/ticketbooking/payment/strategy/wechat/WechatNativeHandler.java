package com.ticketbooking.payment.strategy.wechat;

import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.enums.PayMode;
import com.ticketbooking.common.model.dto.PayResponseDTO;
import com.ticketbooking.common.model.qo.PayRequestQO;
import com.ticketbooking.payment.strategy.PayModeHandler;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 微信 Native 扫码支付 — 调 {@code nativePayService.prepay}，返回 code_url。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.channels.wechat.enabled", havingValue = "true")
public class WechatNativeHandler implements PayModeHandler {

    private final WechatPayProperties properties;
    private final NativePayService nativePayService;

    @Override
    public PayChannel channel() {
        return PayChannel.WECHAT;
    }

    @Override
    public PayMode payMode() {
        return PayMode.WECHAT_NATIVE;
    }

    @Override
    public PayResponseDTO prepay(PayRequestQO request) {
        PrepayRequest prepayRequest = new PrepayRequest();
        prepayRequest.setAppid(properties.getAppId());
        prepayRequest.setMchid(properties.getMchId());
        prepayRequest.setDescription(request.getSubject());
        prepayRequest.setOutTradeNo(request.getOutTradeNo());
        prepayRequest.setNotifyUrl(properties.getNotifyUrl());

        Amount amount = new Amount();
        amount.setTotal(request.getAmount());
        amount.setCurrency("CNY");
        prepayRequest.setAmount(amount);

        PrepayResponse response = nativePayService.prepay(prepayRequest);
        log.info("Wechat Native prepay success: outTradeNo={}, codeUrl={}", request.getOutTradeNo(), response.getCodeUrl());

        return PayResponseDTO.builder().payMode(payMode()).payUrl(response.getCodeUrl()).build();
    }
}

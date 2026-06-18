package com.ticketbooking.payment.strategy.wechat;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.enums.PayMode;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.payment.model.dto.PayResponseDTO;
import com.ticketbooking.payment.model.qo.PayRequestQO;
import com.ticketbooking.payment.strategy.PayModeHandler;
import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信 JSAPI（公众号/小程序）支付 — 必传 openId，调 {@code jsapiService.prepay}，
 * 返回 prepay_id（前端再二次签名唤起支付）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.channels.wechat.enabled", havingValue = "true")
public class WechatJsapiHandler implements PayModeHandler {

    private final WechatPayProperties properties;
    private final JsapiService jsapiService;

    @Override
    public PayChannel channel() {
        return PayChannel.WECHAT;
    }

    @Override
    public PayMode payMode() {
        return PayMode.WECHAT_JSAPI;
    }

    @Override
    public PayResponseDTO prepay(PayRequestQO request) {
        if (request.getOpenId() == null || request.getOpenId().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "微信JSAPI支付必须传入openId");
        }

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

        Payer payer = new Payer();
        payer.setOpenid(request.getOpenId());
        prepayRequest.setPayer(payer);

        PrepayResponse response = jsapiService.prepay(prepayRequest);
        log.info("Wechat JSAPI prepay success: outTradeNo={}, prepayId={}", request.getOutTradeNo(), response.getPrepayId());

        Map<String, String> payParams = new HashMap<>();
        payParams.put("prepay_id", response.getPrepayId());
        return PayResponseDTO.builder().payMode(payMode()).payParams(payParams).build();
    }
}

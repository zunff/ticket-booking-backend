package com.ticketbooking.payment.strategy.wechat;

import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.enums.PayMode;
import com.ticketbooking.payment.model.dto.PayResponseDTO;
import com.ticketbooking.payment.model.qo.PayRequestQO;
import com.ticketbooking.payment.strategy.PayModeHandler;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.h5.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 微信 H5 支付（手机浏览器外置）— 调 {@code h5Service.prepay}，返回 h5_url。
 * 需要用户真实 IP（从 request.extras["client_ip"] 取，由网关透传）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.channels.wechat.enabled", havingValue = "true")
public class WechatH5Handler implements PayModeHandler {

    private final WechatPayProperties properties;
    private final H5Service h5Service;

    @Override
    public PayChannel channel() {
        return PayChannel.WECHAT;
    }

    @Override
    public PayMode payMode() {
        return PayMode.WECHAT_H5;
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

        SceneInfo sceneInfo = new SceneInfo();
        sceneInfo.setPayerClientIp(request.getExtras() != null ? request.getExtras().get("client_ip") : null);
        H5Info h5Info = new H5Info();
        h5Info.setType("Wap");
        sceneInfo.setH5Info(h5Info);
        prepayRequest.setSceneInfo(sceneInfo);

        PrepayResponse response = h5Service.prepay(prepayRequest);
        log.info("Wechat H5 prepay success: outTradeNo={}, h5Url={}", request.getOutTradeNo(), response.getH5Url());

        return PayResponseDTO.builder().payMode(payMode()).payUrl(response.getH5Url()).build();
    }
}

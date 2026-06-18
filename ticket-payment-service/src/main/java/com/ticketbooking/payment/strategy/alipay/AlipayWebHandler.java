package com.ticketbooking.payment.strategy.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.enums.PayMode;
import com.ticketbooking.common.exception.SystemException;
import com.ticketbooking.payment.model.dto.PayResponseDTO;
import com.ticketbooking.payment.model.qo.PayRequestQO;
import com.ticketbooking.payment.strategy.PayModeHandler;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝电脑网站支付（page.pay）— 调 {@code pageExecute}，返回自动提交表单 HTML。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.channels.alipay.enabled", havingValue = "true")
public class AlipayWebHandler implements PayModeHandler {

    private static final String PRODUCT_CODE = "FAST_INSTANT_TRADE_PAY";

    private final AlipayProperties properties;
    private final AlipayClient alipayClient;

    @Override
    public PayChannel channel() {
        return PayChannel.ALIPAY;
    }

    @Override
    public PayMode payMode() {
        return PayMode.ALIPAY_WEB;
    }

    @Override
    public PayResponseDTO prepay(PayRequestQO request) {
        AlipayTradePagePayRequest payRequest = new AlipayTradePagePayRequest();
        payRequest.setNotifyUrl(properties.getNotifyUrl());
        payRequest.setReturnUrl(properties.getReturnUrl());
        payRequest.setBizContent(buildBizContent(request, PRODUCT_CODE));

        try {
            AlipayTradePagePayResponse response = alipayClient.pageExecute(payRequest);
            log.info("Alipay WEB prepay success: outTradeNo={}", request.getOutTradeNo());
            return PayResponseDTO.builder()
                    .payMode(payMode())
                    .payUrl(response.getBody())
                    .build();
        } catch (AlipayApiException e) {
            log.error("Alipay WEB prepay failed: outTradeNo={}", request.getOutTradeNo(), e);
            throw new SystemException(ErrorCode.PAYMENT_FAILED, "支付宝下单失败: " + e.getErrMsg());
        }
    }

    private String buildBizContent(PayRequestQO request, String productCode) {
        Map<String, Object> biz = new HashMap<>();
        biz.put("out_trade_no", request.getOutTradeNo());
        biz.put("total_amount", String.format("%.2f", request.getAmount() / 100.0));
        biz.put("subject", request.getSubject());
        biz.put("product_code", productCode);
        return JSONUtil.toJsonStr(biz);
    }
}

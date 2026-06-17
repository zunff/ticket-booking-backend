package com.ticketbooking.payment.strategy.alipay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "payment.channels.alipay")
public class AlipayProperties {

    private boolean enabled;
    private String appId;
    private String privateKey;
    private String alipayPublicKey;
    private String gatewayUrl;
    private String notifyUrl;
    private String returnUrl;

    /** 签名算法，默认 RSA2(SHA256WithRSA) */
    private String signType = "RSA2";
}

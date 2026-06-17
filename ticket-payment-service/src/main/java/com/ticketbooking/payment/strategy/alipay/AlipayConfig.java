package com.ticketbooking.payment.strategy.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝 SDK 装配。
 * <p>
 * 仅在 {@code payment.channels.alipay.enabled=true} 时生效。
 */
@Configuration
@ConditionalOnProperty(name = "payment.channels.alipay.enabled", havingValue = "true")
public class AlipayConfig {

    @Bean
    public AlipayClient alipayClient(AlipayProperties properties) {
        return new DefaultAlipayClient(
                properties.getGatewayUrl(),
                properties.getAppId(),
                properties.getPrivateKey(),
                "JSON",
                "UTF-8",
                properties.getAlipayPublicKey(),
                properties.getSignType()
        );
    }
}

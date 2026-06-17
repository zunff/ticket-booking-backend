package com.ticketbooking.payment.strategy.wechat;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.refund.RefundService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 微信支付 V3 SDK 装配。
 * <p>
 * 仅在 {@code payment.channels.wechat.enabled=true} 时生效：未配置真实商户凭证时不装配，
 * 调用微信渠道会走工厂的 {@code PAYMENT_CHANNEL_NOT_SUPPORTED}。
 * <p>
 * {@link RSAAutoCertificateConfig} 构造时会自动从微信下载平台证书，需要外网。
 */
@Configuration
@ConditionalOnProperty(name = "payment.channels.wechat.enabled", havingValue = "true")
public class WechatPayConfig {

    @Bean
    public RSAAutoCertificateConfig wechatRsaConfig(WechatPayProperties properties) {
        return new RSAAutoCertificateConfig.Builder()
                .merchantId(properties.getMchId())
                .privateKeyFromPath(properties.getPrivateKeyPath())
                .merchantSerialNumber(properties.getCertSerialNo())
                .apiV3Key(properties.getApiV3Key())
                .build();
    }

    @Bean
    public NativePayService wechatNativePayService(RSAAutoCertificateConfig config) {
        return new NativePayService.Builder().config(config).build();
    }

    @Bean
    public RefundService wechatRefundService(RSAAutoCertificateConfig config) {
        return new RefundService.Builder().config(config).build();
    }

    @Bean
    public NotificationParser wechatNotificationParser(RSAAutoCertificateConfig config) {
        return new NotificationParser(config);
    }
}

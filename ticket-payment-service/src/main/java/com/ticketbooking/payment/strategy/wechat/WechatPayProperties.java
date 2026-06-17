package com.ticketbooking.payment.strategy.wechat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "payment.channels.wechat")
public class WechatPayProperties {

    private boolean enabled;
    private String appId;
    private String mchId;
    private String apiV3Key;
    private String privateKeyPath;
    private String certSerialNo;
    private String notifyUrl;
}

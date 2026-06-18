package com.ticketbooking.payment.strategy.mock;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "payment.mock")
public class MockProperties {

    private boolean enabled = true;

    /**
     * Mock 收银台对外可访问的基础地址，拼成完整 payUrl 返回给前端。
     * 指向 gateway 的 payment 路由前缀（含 /api/payment）。
     * 与 wechat/alipay 的 notifyUrl 同类配置：dev/prod 可不同。
     */
    private String cashierBaseUrl = "http://localhost:9000/api/payment";
}

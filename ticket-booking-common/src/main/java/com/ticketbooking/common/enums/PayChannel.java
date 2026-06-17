package com.ticketbooking.common.enums;

import lombok.Getter;

@Getter
public enum PayChannel {

    WECHAT("wechatpay", "微信支付"),
    ALIPAY("alipay", "支付宝"),
    MOCK("mock", "模拟支付");

    private final String code;
    private final String desc;

    PayChannel(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static PayChannel of(String code) {
        if (code == null) {
            return null;
        }
        for (PayChannel channel : values()) {
            if (channel.code.equals(code)) {
                return channel;
            }
        }
        return null;
    }
}

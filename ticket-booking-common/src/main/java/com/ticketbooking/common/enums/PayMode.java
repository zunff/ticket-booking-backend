package com.ticketbooking.common.enums;

import lombok.Getter;

/**
 * 支付方式。
 * <p>
 * 枚举名前缀标识所属渠道：WECHAT_ 微信独有，ALIPAY_ 支付宝独有，
 * WECHAT_ALIPAY_ 两端共有，MOCK_ 模拟渠道独有。
 */
@Getter
public enum PayMode {

    // 微信独有
    WECHAT_NATIVE("wechat_native", "微信扫码支付"),
    WECHAT_JSAPI("wechat_jsapi", "微信公众号/小程序支付"),
    WECHAT_H5("wechat_h5", "微信H5支付"),

    // 微信 / 支付宝共有
    WECHAT_ALIPAY_APP("wechat_alipay_app", "微信/支付宝APP支付"),

    // 支付宝独有
    ALIPAY_WEB("alipay_web", "支付宝PC网站支付"),
    ALIPAY_WAP("alipay_wap", "支付宝手机网站支付"),

    // 模拟渠道独有
    MOCK_PAGE_CONFIRM("mock_page_confirm", "模拟-收银台页面确认"),
    MOCK_QUICK_SUCCESS("mock_quick_success", "模拟-快速成功"),
    MOCK_QUICK_FAIL("mock_quick_fail", "模拟-快速失败");

    private final String code;
    private final String desc;

    PayMode(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static PayMode of(String code) {
        if (code == null) {
            return null;
        }
        for (PayMode mode : values()) {
            if (mode.code.equals(code)) {
                return mode;
            }
        }
        return null;
    }
}

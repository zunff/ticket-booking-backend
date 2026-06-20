package com.ticketbooking.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("payment_record")
public class PaymentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentNo;

    /** 业务订单号（关联 order 服务，一个 orderNo 可对应多条流水） */
    private String orderNo;

    /** 发给渠道的商户单号，每次下单唯一（取值 = paymentNo） */
    private String outTradeNo;

    /** 支付渠道，存 PayChannel.code (wechatpay/alipay) */
    private String channel;

    /** 支付方式，存 PayMode.code (wechat_native/alipay_web/mock_page_confirm/...) */
    private String payMode;

    private Integer amount;

    private Integer paidAmount;

    /** 支付状态，存 PaymentStatus.code (0-5) */
    private Integer status;

    private String channelTradeNo;

    /** 支付入口地址（code_url/h5_url/收银台地址等），幂等命中时回放给前端 */
    private String payUrl;

    /** 支付唤起参数（如 JSAPI 的 prepay_id），JSON 串 */
    private String payParams;

    private String subject;

    private String openId;

    private String returnUrl;

    private LocalDateTime payTime;

    private LocalDateTime expireTime;

    private String callbackContent;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

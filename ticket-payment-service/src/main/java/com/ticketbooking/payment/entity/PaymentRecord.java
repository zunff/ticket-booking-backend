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

    private String subject;

    private String openId;

    private String returnUrl;

    private LocalDateTime payTime;

    private LocalDateTime expireTime;

    private String callbackContent;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

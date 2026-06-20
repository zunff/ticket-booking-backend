CREATE DATABASE IF NOT EXISTS ticket_payment DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ticket_payment;

CREATE TABLE IF NOT EXISTS payment_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_no VARCHAR(64) NOT NULL UNIQUE COMMENT '支付单号(内部id，同时作为发给渠道的商户单号)',
    order_no VARCHAR(64) NOT NULL COMMENT '业务订单号(关联order服务)',
    out_trade_no VARCHAR(64) NOT NULL COMMENT '发给渠道的商户单号(每次下单唯一，取值=payment_no)',
    channel VARCHAR(32) NOT NULL COMMENT '支付渠道(wechatpay/alipay)',
    pay_mode VARCHAR(32) COMMENT '支付方式(native/jsapi/h5/app/web/wap)',
    amount INT NOT NULL COMMENT '金额(分)',
    paid_amount INT DEFAULT NULL COMMENT '实付金额(分)',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0-待支付 1-支付中 2-成功 3-失败 4-已关闭 5-已退款',
    channel_trade_no VARCHAR(128) DEFAULT NULL COMMENT '渠道交易号',
    pay_url VARCHAR(512) DEFAULT NULL COMMENT '支付入口地址(code_url/h5_url/收银台等)',
    pay_params TEXT DEFAULT NULL COMMENT '支付唤起参数JSON(如JSAPI prepay_id)',
    subject VARCHAR(255) DEFAULT NULL COMMENT '商品描述',
    open_id VARCHAR(128) DEFAULT NULL COMMENT '微信openId(JSAPI)',
    return_url VARCHAR(512) DEFAULT NULL COMMENT '同步跳转地址',
    pay_time DATETIME DEFAULT NULL COMMENT '支付成功时间',
    expire_time DATETIME DEFAULT NULL COMMENT '过期时间',
    callback_content TEXT DEFAULT NULL COMMENT '回调原始内容',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_out_trade_no (out_trade_no),
    INDEX idx_order_no (order_no),
    INDEX idx_channel_trade_no (channel_trade_no),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

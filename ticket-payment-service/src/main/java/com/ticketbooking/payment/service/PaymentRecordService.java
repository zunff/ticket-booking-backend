package com.ticketbooking.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.common.enums.PaymentStatus;
import com.ticketbooking.payment.entity.PaymentRecord;

import java.time.LocalDateTime;
import java.util.Map;

public interface PaymentRecordService extends IService<PaymentRecord> {

    PaymentRecord findByOutTradeNo(String outTradeNo);

    /**
     * 按业务订单号查「在途」流水（PENDING/PROCESSING）最新一条，prepay 幂等复用用。
     */
    PaymentRecord findLiveByOrderNo(String orderNo);

    /**
     * 按业务订单号查最新一条流水（任意状态），query/close/refund/getDetail 解析用。
     */
    PaymentRecord findLatestByOrderNo(String orderNo);

    PaymentRecord findByPaymentNo(String paymentNo);

    void updateChannelTradeNo(String outTradeNo, String channelTradeNo);

    void updatePayInfo(String outTradeNo, String payUrl, Map<String, String> payParams);

    void updateOnNotifySuccess(String outTradeNo, Integer paidAmount, String channelTradeNo, LocalDateTime payTime);

    void updateStatus(String outTradeNo, PaymentStatus status);
}

package com.ticketbooking.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.common.enums.PaymentStatus;
import com.ticketbooking.payment.entity.PaymentRecord;

import java.time.LocalDateTime;

public interface PaymentRecordService extends IService<PaymentRecord> {

    PaymentRecord findByOutTradeNo(String outTradeNo);

    PaymentRecord findByPaymentNo(String paymentNo);

    void updateChannelTradeNo(String outTradeNo, String channelTradeNo);

    void updateOnNotifySuccess(String outTradeNo, Integer paidAmount, String channelTradeNo, LocalDateTime payTime);

    void updateStatus(String outTradeNo, PaymentStatus status);
}

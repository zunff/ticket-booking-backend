package com.ticketbooking.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.enums.PaymentStatus;
import com.ticketbooking.payment.entity.PaymentRecord;
import com.ticketbooking.payment.mapper.PaymentRecordMapper;
import com.ticketbooking.payment.service.PaymentRecordService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PaymentRecordServiceImpl extends ServiceImpl<PaymentRecordMapper, PaymentRecord>
        implements PaymentRecordService {

    @Override
    public PaymentRecord findByOutTradeNo(String outTradeNo) {
        return getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOutTradeNo, outTradeNo));
    }

    @Override
    public PaymentRecord findByPaymentNo(String paymentNo) {
        return getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPaymentNo, paymentNo));
    }

    @Override
    public void updateChannelTradeNo(String outTradeNo, String channelTradeNo) {
        lambdaUpdate()
                .eq(PaymentRecord::getOutTradeNo, outTradeNo)
                .set(PaymentRecord::getChannelTradeNo, channelTradeNo)
                .update();
    }

    @Override
    public void updateOnNotifySuccess(String outTradeNo, Integer paidAmount, String channelTradeNo, LocalDateTime payTime) {
        lambdaUpdate()
                .eq(PaymentRecord::getOutTradeNo, outTradeNo)
                .set(PaymentRecord::getStatus, PaymentStatus.SUCCESS.getCode())
                .set(PaymentRecord::getPaidAmount, paidAmount)
                .set(PaymentRecord::getChannelTradeNo, channelTradeNo)
                .set(PaymentRecord::getPayTime, payTime)
                .update();
    }

    @Override
    public void updateStatus(String outTradeNo, PaymentStatus status) {
        lambdaUpdate()
                .eq(PaymentRecord::getOutTradeNo, outTradeNo)
                .set(PaymentRecord::getStatus, status.getCode())
                .update();
    }
}

package com.ticketbooking.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.enums.PaymentStatus;
import com.ticketbooking.payment.client.OrderServiceClient;
import com.ticketbooking.payment.entity.PaymentRecord;
import com.ticketbooking.payment.mapper.PaymentRecordMapper;
import com.ticketbooking.payment.service.PaymentRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecordServiceImpl extends ServiceImpl<PaymentRecordMapper, PaymentRecord>
        implements PaymentRecordService {

    private final OrderServiceClient orderServiceClient;

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

        // 通知订单服务置为已支付（尽力而为，失败由 order 超时对账 Job 兜底）。
        // 不 re-throw：避免 mock 路径中断页面/prepay；webhook 路径靠网关重试 + 对账双保险。
        try {
            orderServiceClient.markOrderPaid(outTradeNo);
        } catch (Exception e) {
            log.error("Failed to notify order of payment success, reconciliation job will catch this: outTradeNo={}", outTradeNo, e);
        }
    }

    @Override
    public void updateStatus(String outTradeNo, PaymentStatus status) {
        lambdaUpdate()
                .eq(PaymentRecord::getOutTradeNo, outTradeNo)
                .set(PaymentRecord::getStatus, status.getCode())
                .update();
    }
}

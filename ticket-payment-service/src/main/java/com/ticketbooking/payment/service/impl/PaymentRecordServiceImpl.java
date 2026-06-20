package com.ticketbooking.payment.service.impl;

import cn.hutool.json.JSONUtil;
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
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecordServiceImpl extends ServiceImpl<PaymentRecordMapper, PaymentRecord>
        implements PaymentRecordService {

    private final OrderServiceClient orderServiceClient;

    @Override
    public PaymentRecord findByOutTradeNo(String outTradeNo) {
        return getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOutTradeNo, outTradeNo)
                .orderByDesc(PaymentRecord::getId)
                .last("LIMIT 1"));
    }

    @Override
    public PaymentRecord findLiveByOrderNo(String orderNo) {
        return getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderNo, orderNo)
                .in(PaymentRecord::getStatus, PaymentStatus.PENDING.getCode(), PaymentStatus.PROCESSING.getCode())
                .orderByDesc(PaymentRecord::getId)
                .last("LIMIT 1"));
    }

    @Override
    public PaymentRecord findLatestByOrderNo(String orderNo) {
        return getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderNo, orderNo)
                .orderByDesc(PaymentRecord::getId)
                .last("LIMIT 1"));
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
    public void updatePayInfo(String outTradeNo, String payUrl, Map<String, String> payParams) {
        lambdaUpdate()
                .eq(PaymentRecord::getOutTradeNo, outTradeNo)
                .set(PaymentRecord::getPayUrl, payUrl)
                .set(PaymentRecord::getPayParams, payParams != null ? JSONUtil.toJsonStr(payParams) : null)
                .update();
    }

    @Override
    public void updateOnNotifySuccess(String outTradeNo, Integer paidAmount, String channelTradeNo, LocalDateTime payTime) {
        PaymentRecord record = findByOutTradeNo(outTradeNo);
        if (record == null) {
            log.warn("Notify success but payment record not found: outTradeNo={}", outTradeNo);
            return;
        }
        record.setStatus(PaymentStatus.SUCCESS.getCode());
        record.setPaidAmount(paidAmount);
        record.setChannelTradeNo(channelTradeNo);
        record.setPayTime(payTime);
        updateById(record);

        // 通知订单服务置为已支付（用业务订单号，而非渠道商户单号）。尽力而为，失败由 order 超时对账 Job 兜底。
        // 不 re-throw：避免 mock 路径中断页面/prepay；webhook 路径靠网关重试 + 对账双保险。
        try {
            orderServiceClient.markOrderPaid(record.getOrderNo());
        } catch (Exception e) {
            log.error("Failed to notify order of payment success, reconciliation job will catch this: orderNo={}", record.getOrderNo(), e);
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

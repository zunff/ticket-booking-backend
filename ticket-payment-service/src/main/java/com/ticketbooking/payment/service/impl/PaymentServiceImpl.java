package com.ticketbooking.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.payment.converter.PaymentConverter;
import com.ticketbooking.payment.entity.PaymentRecord;
import com.ticketbooking.payment.model.dto.NotifyResultDTO;
import com.ticketbooking.common.model.qo.PayRequestQO;
import com.ticketbooking.common.model.dto.PayResponseDTO;
import com.ticketbooking.common.model.qo.RefundRequestQO;
import com.ticketbooking.common.model.dto.RefundResultDTO;
import com.ticketbooking.common.model.dto.TradeQueryDTO;
import com.ticketbooking.payment.model.vo.PaymentRecordVO;
import com.ticketbooking.payment.service.PaymentRecordService;
import com.ticketbooking.payment.service.PaymentService;
import com.ticketbooking.payment.strategy.CloseCapable;
import com.ticketbooking.payment.strategy.PayChannelFactory;
import com.ticketbooking.payment.strategy.PayChannelStrategy;
import com.ticketbooking.payment.strategy.QueryCapable;
import com.ticketbooking.payment.strategy.RefundCapable;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PayChannelFactory channelFactory;
    private final PaymentRecordService recordService;
    private final PaymentConverter converter;

    @Override
    public PayResponseDTO prepay(PayRequestQO request) {
        return channelFactory.get(getPayChannel(request.getChannel())).prepay(request);
    }

    @Override
    public TradeQueryDTO query(String orderNo, PayChannel channel) {
        PaymentRecord payment = requireLatestByOrderNo(orderNo);
        PayChannelStrategy strategy = channelFactory.get(channel);
        if (strategy instanceof QueryCapable qc) {
            return qc.query(payment.getOutTradeNo());
        }
        throw new BusinessException(ErrorCode.PAYMENT_CAPABILITY_NOT_SUPPORTED, "该渠道不支持订单查询");
    }

    @Override
    public boolean close(String orderNo, PayChannel channel) {
        PaymentRecord payment = requireLatestByOrderNo(orderNo);
        PayChannelStrategy strategy = channelFactory.get(channel);
        if (strategy instanceof CloseCapable cc) {
            return cc.close(payment.getOutTradeNo());
        }
        throw new BusinessException(ErrorCode.PAYMENT_CAPABILITY_NOT_SUPPORTED, "该渠道不支持关闭订单");
    }

    @Override
    public RefundResultDTO refund(RefundRequestQO request) {
        PaymentRecord payment = requireLatestByOrderNo(request.getOrderNo());
        // 回填本次按次唯一的渠道商户单号给策略（渠道按 out_trade_no 定位原交易退款）
        request.setOutTradeNo(payment.getOutTradeNo());
        PayChannelStrategy strategy = channelFactory.get(getPayChannel(request.getChannel()));
        if (strategy instanceof RefundCapable rc) {
            return rc.refund(request);
        }
        throw new BusinessException(ErrorCode.PAYMENT_CAPABILITY_NOT_SUPPORTED, "该渠道不支持退款");
    }

    @NotNull
    private PayChannel getPayChannel(String channel) {
        PayChannel payChannel = PayChannel.of(channel);
        if (payChannel == null) {
            throw new BusinessException(ErrorCode.PAYMENT_CHANNEL_NOT_SUPPORTED);
        }
        return payChannel;
    }

    private PaymentRecord requireLatestByOrderNo(String orderNo) {
        PaymentRecord payment = recordService.findLatestByOrderNo(orderNo);
        if (payment == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return payment;
    }

    @Override
    public NotifyResultDTO handleNotify(PayChannel channel, HttpServletRequest request) {
        return channelFactory.get(channel).parseNotify(request);
    }

    @Override
    public String buildNotifyAck(PayChannel channel, NotifyResultDTO result) {
        return channelFactory.get(channel).buildAckResponse(result);
    }

    @Override
    public PaymentRecordVO getDetail(String orderNo) {
        PaymentRecord record = recordService.findLatestByOrderNo(orderNo);
        if (record == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return converter.toDTO(record);
    }

    @Override
    public PageResult<PaymentRecordVO> getPage(Long current, Long size, String orderNo, PayChannel channel, Integer status) {
        LambdaQueryWrapper<PaymentRecord> wrapper = new LambdaQueryWrapper<>();
        if (orderNo != null && !orderNo.isBlank()) {
            wrapper.eq(PaymentRecord::getOrderNo, orderNo);
        }
        if (channel != null) {
            wrapper.eq(PaymentRecord::getChannel, channel.getCode());
        }
        if (status != null) {
            wrapper.eq(PaymentRecord::getStatus, status);
        }
        wrapper.orderByDesc(PaymentRecord::getCreateTime);

        IPage<PaymentRecord> page = recordService.page(new Page<>(current, size), wrapper);
        return PageResult.of(
                page.getRecords().stream().map(converter::toDTO).toList(),
                page.getTotal(),
                current,
                size
        );
    }
}

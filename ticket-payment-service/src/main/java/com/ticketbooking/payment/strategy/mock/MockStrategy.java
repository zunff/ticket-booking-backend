package com.ticketbooking.payment.strategy.mock;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.enums.PayMode;
import com.ticketbooking.common.enums.PaymentStatus;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.payment.entity.PaymentRecord;
import com.ticketbooking.payment.model.dto.NotifyResultDTO;
import com.ticketbooking.common.model.dto.PayResponseDTO;
import com.ticketbooking.common.model.dto.RefundResultDTO;
import com.ticketbooking.common.model.dto.TradeQueryDTO;
import com.ticketbooking.common.model.qo.PayRequestQO;
import com.ticketbooking.common.model.qo.RefundRequestQO;
import com.ticketbooking.payment.service.PaymentRecordService;
import com.ticketbooking.payment.strategy.AbstractPayChannelStrategy;
import com.ticketbooking.payment.strategy.CloseCapable;
import com.ticketbooking.payment.strategy.QueryCapable;
import com.ticketbooking.payment.strategy.RefundCapable;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "true", matchIfMissing = true)
public class MockStrategy extends AbstractPayChannelStrategy
        implements QueryCapable, CloseCapable, RefundCapable {

    private final MockProperties mockProperties;

    public MockStrategy(MockProperties mockProperties,
                        PaymentRecordService recordService, RedissonClient redissonClient) {
        super(recordService, redissonClient);
        this.mockProperties = mockProperties;
    }

    @Override
    public PayChannel channel() {
        return PayChannel.MOCK;
    }

    @Override
    protected PayMode getDefaultPayMode() {
        return PayMode.MOCK_PAGE_CONFIRM;
    }

    @Override
    protected PayResponseDTO doPrepay(PayRequestQO request) {
        PayMode payMode = inferPayMode(request);
        String channelTradeNo = "MOCK_" + request.getOutTradeNo();
        String payUrl = (payMode == PayMode.MOCK_PAGE_CONFIRM)
                ? buildCashierUrl(request.getOutTradeNo())
                : null;
        return PayResponseDTO.builder()
                .channelTradeNo(channelTradeNo)
                .payMode(payMode)
                .payUrl(payUrl)
                .build();
    }

    private String buildCashierUrl(String outTradeNo) {
        String base = mockProperties.getCashierBaseUrl();
        if (base == null || base.isBlank()) {
            base = "";
        }
        // 去掉末尾斜杠，避免拼接出双斜杠
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return base + "/mock/cashier/" + outTradeNo;
    }

    @Override
    protected void finalizeAfterPrepay(PayRequestQO request, PayResponseDTO response) {
        if (response.getChannelTradeNo() != null) {
            recordService.updateChannelTradeNo(request.getOutTradeNo(), response.getChannelTradeNo());
        }
        switch (inferPayMode(request)) {
            case MOCK_QUICK_SUCCESS ->
                recordService.updateOnNotifySuccess(
                        request.getOutTradeNo(),
                        request.getAmount(),
                        response.getChannelTradeNo(),
                        LocalDateTime.now());
            case MOCK_QUICK_FAIL -> recordService.updateStatus(request.getOutTradeNo(), PaymentStatus.FAILED);
            default -> recordService.updateStatus(request.getOutTradeNo(), PaymentStatus.PROCESSING);
        }
    }

    @Override
    protected NotifyResultDTO doParseNotify(HttpServletRequest request) {
        throw new BusinessException(ErrorCode.PAYMENT_CAPABILITY_NOT_SUPPORTED, "Mock渠道不支持HTTP通知回调，请使用收银台页面操作");
    }

    @Override
    public String buildAckResponse(NotifyResultDTO result) {
        return "mock-ok";
    }

    @Override
    public TradeQueryDTO query(String outTradeNo) {
        PaymentRecord record = requireRecord(outTradeNo);
        return TradeQueryDTO.builder()
                .status(PaymentStatus.of(record.getStatus()))
                .channelTradeNo(record.getChannelTradeNo())
                .paidAmount(record.getPaidAmount())
                .payTime(record.getPayTime())
                .build();
    }

    @Override
    public boolean close(String outTradeNo) {
        recordService.updateStatus(outTradeNo, PaymentStatus.CLOSED);
        return true;
    }

    @Override
    public RefundResultDTO refund(RefundRequestQO request) {
        recordService.updateStatus(request.getOutTradeNo(), PaymentStatus.REFUNDED);
        return RefundResultDTO.builder()
                .success(true)
                .refundNo(request.getRefundNo())
                .channelRefundNo("MOCKRF_" + request.getRefundNo())
                .refundAmount(request.getRefundAmount())
                .refundTime(LocalDateTime.now())
                .build();
    }

    private PaymentRecord requireRecord(String outTradeNo) {
        PaymentRecord record = recordService.findByOutTradeNo(outTradeNo);
        if (record == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return record;
    }
}

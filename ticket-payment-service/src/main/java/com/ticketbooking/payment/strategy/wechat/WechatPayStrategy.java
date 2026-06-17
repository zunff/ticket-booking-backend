package com.ticketbooking.payment.strategy.wechat;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.enums.PayMode;
import com.ticketbooking.common.enums.PaymentStatus;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.payment.model.dto.NotifyResultDTO;
import com.ticketbooking.payment.model.dto.PayResponseDTO;
import com.ticketbooking.payment.model.dto.RefundResultDTO;
import com.ticketbooking.payment.model.dto.TradeQueryDTO;
import com.ticketbooking.payment.model.qo.PayRequestQO;
import com.ticketbooking.payment.model.qo.RefundRequestQO;
import com.ticketbooking.payment.service.PaymentRecordService;
import com.ticketbooking.payment.strategy.AbstractPayChannelStrategy;
import com.ticketbooking.payment.strategy.CloseCapable;
import com.ticketbooking.payment.strategy.QueryCapable;
import com.ticketbooking.payment.strategy.RefundCapable;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 微信支付策略 — V3 Native 扫码支付。
 * <p>
 * 实现：基接口 + QueryCapable + CloseCapable + RefundCapable。
 * 仅当 {@code payment.channels.wechat.enabled=true} 时装配。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.channels.wechat.enabled", havingValue = "true")
public class WechatPayStrategy extends AbstractPayChannelStrategy
        implements QueryCapable, CloseCapable, RefundCapable {

    private final WechatPayProperties properties;
    private final NativePayService nativePayService;
    private final RefundService refundService;
    private final NotificationParser notificationParser;

    /**
     * 微信通知验签 + 解密由 {@link NotificationParser#parse} 一步完成，
     * 但模板方法拆分为 verifySignature / doParseNotify 两步。
     * 用 ThreadLocal 在两步间传递解析结果，doParseNotify 消费后立即清理。
     */
    private final ThreadLocal<Transaction> notifyTransactionHolder = new ThreadLocal<>();

    public WechatPayStrategy(WechatPayProperties properties,
                             NativePayService nativePayService,
                             RefundService refundService,
                             NotificationParser notificationParser,
                             PaymentRecordService recordService,
                             RedissonClient redissonClient) {
        super(recordService, redissonClient);
        this.properties = properties;
        this.nativePayService = nativePayService;
        this.refundService = refundService;
        this.notificationParser = notificationParser;
    }

    @Override
    public PayChannel channel() {
        return PayChannel.WECHAT;
    }

    @Override
    protected PayMode getDefaultPayMode() {
        return PayMode.WECHAT_NATIVE;
    }

    // ======================== 基接口钩子 ========================

    @Override
    protected PayResponseDTO doPrepay(PayRequestQO request) {
        PayMode payMode = inferPayMode(request);
        if (payMode != PayMode.WECHAT_NATIVE) {
            throw new BusinessException(ErrorCode.PAYMENT_CAPABILITY_NOT_SUPPORTED, "微信当前仅支持Native扫码支付");
        }

        PrepayRequest prepayRequest = new PrepayRequest();
        prepayRequest.setAppid(properties.getAppId());
        prepayRequest.setMchid(properties.getMchId());
        prepayRequest.setDescription(request.getSubject());
        prepayRequest.setOutTradeNo(request.getOutTradeNo());
        prepayRequest.setNotifyUrl(properties.getNotifyUrl());

        Amount amount = new Amount();
        amount.setTotal(request.getAmount());
        amount.setCurrency("CNY");
        prepayRequest.setAmount(amount);

        PrepayResponse response = nativePayService.prepay(prepayRequest);
        log.info("Wechat prepay success: outTradeNo={}, codeUrl={}", request.getOutTradeNo(), response.getCodeUrl());

        return PayResponseDTO.builder()
                .payMode(payMode)
                .payUrl(response.getCodeUrl())
                .build();
    }

    @Override
    protected boolean verifySignature(HttpServletRequest request) {
        try {
            String body = readBody(request);
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(request.getHeader("Wechatpay-Serial"))
                    .nonce(request.getHeader("Wechatpay-Nonce"))
                    .timestamp(request.getHeader("Wechatpay-Timestamp"))
                    .signature(request.getHeader("Wechatpay-Signature"))
                    .signType(request.getHeader("Wechatpay-Signature-Type"))
                    .body(body)
                    .build();

            Transaction transaction = notificationParser.parse(requestParam, Transaction.class);
            notifyTransactionHolder.set(transaction);
            return true;
        } catch (Exception e) {
            log.warn("Wechat notify signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    protected NotifyResultDTO doParseNotify(HttpServletRequest request) {
        Transaction transaction = notifyTransactionHolder.get();
        notifyTransactionHolder.remove();

        boolean success = transaction.getTradeState() == Transaction.TradeStateEnum.SUCCESS;
        Integer paidAmount = transaction.getAmount() != null ? transaction.getAmount().getTotal() : null;
        LocalDateTime payTime = parseRfc3339(transaction.getSuccessTime());

        return NotifyResultDTO.builder()
                .success(success)
                .outTradeNo(transaction.getOutTradeNo())
                .channelTradeNo(transaction.getTransactionId())
                .paidAmount(paidAmount)
                .payTime(payTime)
                .build();
    }

    @Override
    public String buildAckResponse(NotifyResultDTO result) {
        return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
    }

    // ======================== 能力子接口 ========================

    @Override
    public TradeQueryDTO query(String outTradeNo) {
        QueryOrderByOutTradeNoRequest req = new QueryOrderByOutTradeNoRequest();
        req.setOutTradeNo(outTradeNo);
        req.setMchid(properties.getMchId());

        Transaction transaction = nativePayService.queryOrderByOutTradeNo(req);
        return TradeQueryDTO.builder()
                .status(mapTradeState(transaction.getTradeState()))
                .channelTradeNo(transaction.getTransactionId())
                .paidAmount(transaction.getAmount() != null ? transaction.getAmount().getTotal() : null)
                .payTime(parseRfc3339(transaction.getSuccessTime()))
                .build();
    }

    @Override
    public boolean close(String outTradeNo) {
        CloseOrderRequest req = new CloseOrderRequest();
        req.setOutTradeNo(outTradeNo);
        req.setMchid(properties.getMchId());

        nativePayService.closeOrder(req);
        recordService.updateStatus(outTradeNo, PaymentStatus.CLOSED);
        return true;
    }

    @Override
    public RefundResultDTO refund(RefundRequestQO request) {
        CreateRequest req = new CreateRequest();
        req.setOutTradeNo(request.getOutTradeNo());
        req.setOutRefundNo(request.getRefundNo());
        req.setReason(request.getReason());

        com.wechat.pay.java.service.refund.model.AmountReq amount = new com.wechat.pay.java.service.refund.model.AmountReq();
        amount.setRefund(request.getRefundAmount().longValue());
        if (request.getTotalAmount() != null) {
            amount.setTotal(request.getTotalAmount().longValue());
        }
        amount.setCurrency("CNY");
        req.setAmount(amount);

        com.wechat.pay.java.service.refund.model.Refund refund = refundService.create(req);
        boolean success = refund.getStatus() == com.wechat.pay.java.service.refund.model.Status.SUCCESS
                || refund.getStatus() == com.wechat.pay.java.service.refund.model.Status.PROCESSING;

        return RefundResultDTO.builder()
                .success(success)
                .refundNo(request.getRefundNo())
                .channelRefundNo(refund.getRefundId())
                .refundAmount(request.getRefundAmount())
                .refundTime(parseRfc3339(refund.getSuccessTime()))
                .build();
    }

    // ======================== 辅助 ========================

    private PaymentStatus mapTradeState(Transaction.TradeStateEnum tradeState) {
        return switch (tradeState) {
            case SUCCESS -> PaymentStatus.SUCCESS;
            case REFUND -> PaymentStatus.REFUNDED;
            case NOTPAY -> PaymentStatus.PENDING;
            case CLOSED, REVOKED -> PaymentStatus.CLOSED;
            case USERPAYING, ACCEPT -> PaymentStatus.PROCESSING;
            case PAYERROR -> PaymentStatus.FAILED;
        };
    }

    private LocalDateTime parseRfc3339(String rfc3339) {
        if (rfc3339 == null || rfc3339.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rfc3339, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse Wechat time: {}", rfc3339, e);
            return null;
        }
    }

    private String readBody(HttpServletRequest request) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}

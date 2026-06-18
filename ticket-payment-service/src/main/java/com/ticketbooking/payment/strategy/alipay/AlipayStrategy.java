package com.ticketbooking.payment.strategy.alipay;

import cn.hutool.json.JSONUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.enums.PayMode;
import com.ticketbooking.common.enums.PaymentStatus;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.exception.SystemException;
import com.ticketbooking.payment.model.dto.NotifyResultDTO;
import com.ticketbooking.common.model.dto.PayResponseDTO;
import com.ticketbooking.common.model.dto.RefundResultDTO;
import com.ticketbooking.common.model.dto.TradeQueryDTO;
import com.ticketbooking.common.model.qo.PayRequestQO;
import com.ticketbooking.common.model.qo.RefundRequestQO;
import com.ticketbooking.payment.service.PaymentRecordService;
import com.ticketbooking.payment.strategy.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝策略 — 支持 WEB / WAP。
 * <p>
 * prepay 路由到 {@link PayModeHandlerFactory}，各支付方式由独立 Handler Bean 实现。
 * 本类负责通知验签/解析 + query/close/refund 能力。
 * <p>
 * 金额单位：我方与微信都是分，支付宝是元字符串（两位小数），边界处转换。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.channels.alipay.enabled", havingValue = "true")
public class AlipayStrategy extends AbstractPayChannelStrategy
        implements QueryCapable, CloseCapable, RefundCapable {

    private final AlipayProperties properties;
    private final AlipayClient alipayClient;
    private final PayModeHandlerFactory modeHandlerFactory;

    public AlipayStrategy(AlipayProperties properties,
                          AlipayClient alipayClient,
                          PayModeHandlerFactory modeHandlerFactory,
                          PaymentRecordService recordService,
                          RedissonClient redissonClient) {
        super(recordService, redissonClient);
        this.properties = properties;
        this.alipayClient = alipayClient;
        this.modeHandlerFactory = modeHandlerFactory;
    }

    @Override
    public PayChannel channel() {
        return PayChannel.ALIPAY;
    }

    @Override
    protected PayMode getDefaultPayMode() {
        return PayMode.ALIPAY_WEB;
    }

    // ======================== 基接口钩子 ========================

    @Override
    protected PayResponseDTO doPrepay(PayRequestQO request) {
        return modeHandlerFactory.get(channel(), inferPayMode(request)).prepay(request);
    }

    @Override
    protected NotifyResultDTO doParseNotify(HttpServletRequest request) {
        // 1. 验签
        Map<String, String> params = extractParams(request);
        try {
            boolean valid = AlipaySignature.rsaCheckV1(params, properties.getAlipayPublicKey(), "UTF-8", properties.getSignType());
            if (!valid) {
                log.warn("Alipay notify signature verification failed");
                throw new BusinessException(ErrorCode.PAYMENT_FAILED, "通知验签失败");
            }
        } catch (AlipayApiException e) {
            log.warn("Alipay notify signature verification failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_FAILED, "通知验签失败");
        }

        // 2. 解析
        String outTradeNo = request.getParameter("out_trade_no");
        String tradeNo = request.getParameter("trade_no");
        String totalAmount = request.getParameter("total_amount");
        String tradeStatus = request.getParameter("trade_status");

        boolean success = "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
        String gmtPayment = request.getParameter("gmt_payment");

        return NotifyResultDTO.builder()
                .success(success)
                .outTradeNo(outTradeNo)
                .channelTradeNo(tradeNo)
                .paidAmount(yuanToFen(totalAmount))
                .payTime(parseDateStr(gmtPayment))
                .build();
    }

    @Override
    public String buildAckResponse(NotifyResultDTO result) {
        return "success";
    }

    // ======================== 能力子接口 ========================

    @Override
    public TradeQueryDTO query(String outTradeNo) {
        Map<String, Object> biz = new HashMap<>();
        biz.put("out_trade_no", outTradeNo);

        AlipayTradeQueryRequest queryRequest = new AlipayTradeQueryRequest();
        queryRequest.setBizContent(toJson(biz));

        try {
            AlipayTradeQueryResponse response = alipayClient.execute(queryRequest);
            if (!response.isSuccess()) {
                log.warn("Alipay query not success: outTradeNo={}, code={}, subCode={}",
                        outTradeNo, response.getCode(), response.getSubCode());
            }
            return TradeQueryDTO.builder()
                    .status(mapTradeStatus(response.getTradeStatus()))
                    .channelTradeNo(response.getTradeNo())
                    .paidAmount(yuanToFen(response.getTotalAmount()))
                    .payTime(parseDate(response.getSendPayDate()))
                    .build();
        } catch (AlipayApiException e) {
            log.error("Alipay query failed: outTradeNo={}", outTradeNo, e);
            throw new SystemException(ErrorCode.PAYMENT_FAILED, "支付宝查询失败: " + e.getErrMsg());
        }
    }

    @Override
    public boolean close(String outTradeNo) {
        Map<String, Object> biz = new HashMap<>();
        biz.put("out_trade_no", outTradeNo);

        AlipayTradeCloseRequest closeRequest = new AlipayTradeCloseRequest();
        closeRequest.setBizContent(toJson(biz));

        try {
            AlipayTradeCloseResponse response = alipayClient.execute(closeRequest);
            if (!response.isSuccess()) {
                log.warn("Alipay close not success: outTradeNo={}, code={}, subCode={}",
                        outTradeNo, response.getCode(), response.getSubCode());
                return false;
            }
            recordService.updateStatus(outTradeNo, PaymentStatus.CLOSED);
            return true;
        } catch (AlipayApiException e) {
            log.error("Alipay close failed: outTradeNo={}", outTradeNo, e);
            throw new SystemException(ErrorCode.PAYMENT_FAILED, "支付宝关单失败: " + e.getErrMsg());
        }
    }

    @Override
    public RefundResultDTO refund(RefundRequestQO request) {
        Map<String, Object> biz = new HashMap<>();
        biz.put("out_trade_no", request.getOutTradeNo());
        biz.put("refund_amount", fenToYuan(request.getRefundAmount()));
        biz.put("out_request_no", request.getRefundNo());
        if (request.getReason() != null && !request.getReason().isBlank()) {
            biz.put("refund_reason", request.getReason());
        }

        AlipayTradeRefundRequest refundRequest = new AlipayTradeRefundRequest();
        refundRequest.setBizContent(toJson(biz));

        try {
            AlipayTradeRefundResponse response = alipayClient.execute(refundRequest);
            boolean success = response.isSuccess() && "Y".equals(response.getFundChange());
            if (!response.isSuccess()) {
                log.warn("Alipay refund not success: outTradeNo={}, code={}, subCode={}",
                        request.getOutTradeNo(), response.getCode(), response.getSubCode());
            }
            recordService.updateStatus(request.getOutTradeNo(), PaymentStatus.REFUNDED);
            return RefundResultDTO.builder()
                    .success(success)
                    .refundNo(request.getRefundNo())
                    .channelRefundNo(response.getTradeNo())
                    .refundAmount(request.getRefundAmount())
                    .refundTime(parseDate(response.getGmtRefundPay()))
                    .build();
        } catch (AlipayApiException e) {
            log.error("Alipay refund failed: outTradeNo={}", request.getOutTradeNo(), e);
            throw new SystemException(ErrorCode.PAYMENT_FAILED, "支付宝退款失败: " + e.getErrMsg());
        }
    }

    // ======================== 辅助 ========================

    private PaymentStatus mapTradeStatus(String tradeStatus) {
        if (tradeStatus == null) {
            return PaymentStatus.PENDING;
        }
        return switch (tradeStatus) {
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> PaymentStatus.SUCCESS;
            case "WAIT_BUYER_PAY" -> PaymentStatus.PENDING;
            case "TRADE_CLOSED" -> PaymentStatus.CLOSED;
            default -> PaymentStatus.PENDING;
        };
    }

    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            String[] values = entry.getValue();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(values[i]);
            }
            params.put(entry.getKey(), sb.toString());
        }
        return params;
    }

    private LocalDateTime parseDate(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private LocalDateTime parseDateStr(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            log.warn("Failed to parse Alipay date: {}", dateStr, e);
            return null;
        }
    }

    private String fenToYuan(Integer fen) {
        if (fen == null) {
            return "0.00";
        }
        return String.format("%.2f", fen / 100.0);
    }

    private int yuanToFen(String yuan) {
        if (yuan == null || yuan.isBlank()) {
            return 0;
        }
        return (int) Math.round(Double.parseDouble(yuan) * 100);
    }

    private String toJson(Map<String, Object> biz) {
        return JSONUtil.toJsonStr(biz);
    }
}

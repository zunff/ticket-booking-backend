package com.ticketbooking.payment.strategy;

import cn.hutool.core.util.StrUtil;
import com.ticketbooking.common.constant.RedisKeyConstants;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.PayMode;
import com.ticketbooking.common.enums.PaymentStatus;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.payment.entity.PaymentRecord;
import com.ticketbooking.payment.model.dto.NotifyResultDTO;
import com.ticketbooking.payment.model.qo.PayRequestQO;
import com.ticketbooking.payment.model.dto.PayResponseDTO;
import com.ticketbooking.payment.service.PaymentRecordService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 支付渠道策略抽象基类 — 模板方法。
 * <p>
 * 只实现基接口 {@link PayChannelStrategy}，编排 prepay/notify 的公共骨架
 * （幂等检查、分布式锁、本地记录管理、状态流转）。
 * 渠道差异通过抽象钩子留给子类。
 * <p>
 * 能力子接口（QueryCapable/CloseCapable/RefundCapable）的方法不在此类中，
 * 由具体渠道类按需直接实现，保持接口隔离。
 */
@Slf4j
public abstract class AbstractPayChannelStrategy implements PayChannelStrategy {

    private static final DateTimeFormatter PAYMENT_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    protected final PaymentRecordService recordService;
    protected final RedissonClient redissonClient;

    protected AbstractPayChannelStrategy(PaymentRecordService recordService, RedissonClient redissonClient) {
        this.recordService = recordService;
        this.redissonClient = redissonClient;
    }

    // ======================== prepay 模板 ========================

    @Override
    public final PayResponseDTO prepay(PayRequestQO request) {
        // 1. 幂等：已有有效记录直接返回
        PaymentRecord existing = recordService.findByOutTradeNo(request.getOutTradeNo());
        if (existing != null && isLiveState(existing.getStatus())) {
            log.info("Prepay idempotent hit: outTradeNo={}, status={}", request.getOutTradeNo(), existing.getStatus());
            return buildPayResponse(existing);
        }

        // 2. 分布式锁
        String lockKey = RedisKeyConstants.buildPaymentPrepayLockKey(request.getOutTradeNo());
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_BUSY);
        }
        if (!acquired) {
            throw new BusinessException(ErrorCode.SYSTEM_BUSY);
        }

        try {
            // double-check after lock
            existing = recordService.findByOutTradeNo(request.getOutTradeNo());
            if (existing != null && isLiveState(existing.getStatus())) {
                return buildPayResponse(existing);
            }

            // 3. 创建本地记录
            PaymentRecord record = buildPaymentRecord(request);
            recordService.save(record);

            // 4. 调渠道统一下单
            PayResponseDTO response = doPrepay(request);

            // 5. 收尾：写渠道单号 + 置状态（默认 PROCESSING，子类可覆盖以同步到达终态）
            finalizeAfterPrepay(request, response);

            return PayResponseDTO.builder()
                    .paymentNo(record.getPaymentNo())
                    .channelTradeNo(response.getChannelTradeNo())
                    .payMode(response.getPayMode())
                    .payUrl(response.getPayUrl())
                    .payParams(response.getPayParams())
                    .build();

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 子类实现：调渠道统一下单 API
     */
    protected abstract PayResponseDTO doPrepay(PayRequestQO request);

    /**
     * 子类可覆盖：prepay 调渠道后的本地记录收尾。
     * <p>
     * 默认：写入渠道单号 + 置 PROCESSING（等待用户支付）。Mock 等同步渠道可覆盖此方法，
     * 让 prepay 一返回订单即到达终态（SUCCESS/FAILED），无需后续异步通知或页面交互。
     */
    protected void finalizeAfterPrepay(PayRequestQO request, PayResponseDTO response) {
        if (response.getChannelTradeNo() != null) {
            recordService.updateChannelTradeNo(request.getOutTradeNo(), response.getChannelTradeNo());
        }
        recordService.updateStatus(request.getOutTradeNo(), PaymentStatus.PROCESSING);
    }

    // ======================== parseNotify 模板 ========================

    @Override
    public final NotifyResultDTO parseNotify(HttpServletRequest httpRequest) {
        // 1. 验签
        boolean valid = verifySignature(httpRequest);
        if (!valid) {
            log.warn("Notify signature verification failed: channel={}", channel());
            throw new BusinessException(ErrorCode.PAYMENT_FAILED, "通知验签失败");
        }

        // 2. 解析通知内容
        NotifyResultDTO result = doParseNotify(httpRequest);

        if (result.isSuccess()) {
            // 3. 更新本地记录
            recordService.updateOnNotifySuccess(result.getOutTradeNo(), result.getPaidAmount(), result.getChannelTradeNo(), result.getPayTime());
            log.info("Notify success: outTradeNo={}, channelTradeNo={}", result.getOutTradeNo(), result.getChannelTradeNo());
        } else {
            recordService.updateStatus(result.getOutTradeNo(), PaymentStatus.FAILED);
            log.info("Notify failed: outTradeNo={}", result.getOutTradeNo());
        }

        return result;
    }

    /**
     * 子类实现：验证通知签名
     */
    protected abstract boolean verifySignature(HttpServletRequest request);

    /**
     * 子类实现：解析通知内容
     */
    protected abstract NotifyResultDTO doParseNotify(HttpServletRequest request);

    // ======================== 辅助方法 ========================

    private boolean isLiveState(Integer statusCode) {
        return statusCode != null
                && (statusCode == PaymentStatus.PENDING.getCode()
                || statusCode == PaymentStatus.PROCESSING.getCode()
                || statusCode == PaymentStatus.SUCCESS.getCode());
    }

    private PayResponseDTO buildPayResponse(PaymentRecord record) {
        return PayResponseDTO.builder()
                .paymentNo(record.getPaymentNo())
                .channelTradeNo(record.getChannelTradeNo())
                .payMode(PayMode.of(record.getPayMode()))
                .build();
    }

    private PaymentRecord buildPaymentRecord(PayRequestQO request) {
        PaymentRecord record = new PaymentRecord();
        record.setPaymentNo(generatePaymentNo());
        record.setOutTradeNo(request.getOutTradeNo());
        record.setChannel(request.getChannel());
        record.setPayMode(inferPayMode(request).getCode());
        record.setAmount(request.getAmount());
        record.setStatus(PaymentStatus.PENDING.getCode());
        record.setSubject(request.getSubject());
        record.setOpenId(request.getOpenId());
        record.setReturnUrl(request.getReturnUrl());
        record.setExpireTime(LocalDateTime.now().plusMinutes(30));
        return record;
    }

    /**
     * 推断支付方式：显式指定 > 渠道默认
     */
    protected PayMode inferPayMode(PayRequestQO request) {
        if (StrUtil.isNotBlank(request.getPayMode())) {
            PayMode payMode = PayMode.of(request.getPayMode());
            if (payMode != null) {
                return payMode;
            }
        }
        return getDefaultPayMode();
    }

    /**
     * 子类必须实现：该渠道默认支付方式
     */
    protected abstract PayMode getDefaultPayMode();

    private String generatePaymentNo() {
        return "PAY" + LocalDateTime.now().format(PAYMENT_NO_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}

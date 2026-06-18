package com.ticketbooking.payment.service;

import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.payment.model.dto.NotifyResultDTO;
import com.ticketbooking.common.model.qo.PayRequestQO;
import com.ticketbooking.common.model.dto.PayResponseDTO;
import com.ticketbooking.common.model.qo.RefundRequestQO;
import com.ticketbooking.common.model.dto.RefundResultDTO;
import com.ticketbooking.common.model.dto.TradeQueryDTO;
import com.ticketbooking.payment.model.vo.PaymentRecordVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 支付门面服务 — 对外统一入口。
 * <p>
 * 通过 {@link com.ticketbooking.payment.strategy.PayChannelFactory} 获取渠道策略，
 * 对可选能力用 instanceof 探测，未实现则抛 PAYMENT_CAPABILITY_NOT_SUPPORTED。
 */
public interface PaymentService {

    PayResponseDTO prepay(PayRequestQO request);

    TradeQueryDTO query(String outTradeNo, PayChannel channel);

    boolean close(String outTradeNo, PayChannel channel);

    RefundResultDTO refund(RefundRequestQO request);

    NotifyResultDTO handleNotify(PayChannel channel, HttpServletRequest request);

    String buildNotifyAck(PayChannel channel, NotifyResultDTO result);

    PaymentRecordVO getDetail(String outTradeNo);

    PageResult<PaymentRecordVO> getPage(Long current, Long size, String outTradeNo, PayChannel channel, Integer status);
}

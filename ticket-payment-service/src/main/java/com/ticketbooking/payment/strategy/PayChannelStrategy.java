package com.ticketbooking.payment.strategy;

import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.payment.model.dto.NotifyResultDTO;
import com.ticketbooking.common.model.qo.PayRequestQO;
import com.ticketbooking.common.model.dto.PayResponseDTO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 支付渠道策略基接口 — 定义所有渠道都必须具备的核心能力。
 * <p>
 * 只包含三件事：标识渠道、发起支付、解析异步通知。这是任何真实支付渠道的"最小公约数"。
 * <p>
 * 渠道间的可选能力差异（查询/关闭/退款/对账/预授权等）通过继承本接口的
 * 能力子接口（{@link QueryCapable} / {@link CloseCapable} / {@link RefundCapable} 等）
 * 按需实现，调用方用 instanceof 探测，避免胖接口强制每个渠道实现全部方法。
 */
public interface PayChannelStrategy {

    /**
     * 渠道标识
     */
    PayChannel channel();

    /**
     * 统一下单 — 所有渠道必备
     */
    PayResponseDTO prepay(PayRequestQO request);

    /**
     * 解析异步通知 — 所有渠道必备
     */
    NotifyResultDTO parseNotify(HttpServletRequest httpRequest);

    /**
     * 构建通知应答（返回给渠道的 ack 内容） — 所有渠道必备
     */
    String buildAckResponse(NotifyResultDTO result);
}

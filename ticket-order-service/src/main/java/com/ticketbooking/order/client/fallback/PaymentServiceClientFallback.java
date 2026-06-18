package com.ticketbooking.order.client.fallback;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.FeignFallbackException;
import com.ticketbooking.common.model.dto.PayResponseDTO;
import com.ticketbooking.common.model.dto.RefundResultDTO;
import com.ticketbooking.common.model.dto.TradeQueryDTO;
import com.ticketbooking.common.model.qo.PayRequestQO;
import com.ticketbooking.common.model.qo.RefundRequestQO;
import com.ticketbooking.common.sentinel.FeignFallbackFactory;
import com.ticketbooking.order.client.PaymentServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentServiceClientFallback extends FeignFallbackFactory<PaymentServiceClient> {

    public PaymentServiceClientFallback() {
        super("ticket-payment-service");
    }

    @Override
    public PaymentServiceClient create(Throwable cause) {
        logFallback(cause);
        return new PaymentServiceClient() {
            @Override
            public PayResponseDTO prepay(PayRequestQO request) {
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }

            @Override
            public TradeQueryDTO query(String outTradeNo, String channel) {
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }

            @Override
            public Boolean close(String outTradeNo, String channel) {
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }

            @Override
            public RefundResultDTO refund(RefundRequestQO request) {
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }
        };
    }
}

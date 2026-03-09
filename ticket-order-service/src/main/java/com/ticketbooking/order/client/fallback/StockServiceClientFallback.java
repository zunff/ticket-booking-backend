package com.ticketbooking.order.client.fallback;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.FeignFallbackException;
import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.common.sentinel.FeignFallbackFactory;
import com.ticketbooking.order.client.StockServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * StockServiceClient 降级处理 (ticket-order-service)
 */
@Slf4j
@Component
public class StockServiceClientFallback extends FeignFallbackFactory<StockServiceClient> {

    public StockServiceClientFallback() {
        super("ticket-stock-service");
    }

    @Override
    public StockServiceClient create(Throwable cause) {
        logFallback(cause);
        return (concertId, gradeId) -> {
            log.warn("[{}] 查询库存降级: concertId={}, gradeId={}", serviceName, concertId, gradeId);
            throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
        };
    }
}

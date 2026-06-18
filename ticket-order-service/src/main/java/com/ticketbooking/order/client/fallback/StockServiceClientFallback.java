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
        return new StockServiceClient() {
            @Override
            public StockDTO getStock(Long concertId, Long gradeId) {
                log.warn("[{}] 查询库存降级: concertId={}, gradeId={}", serviceName, concertId, gradeId);
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }

            @Override
            public Void restoreStock(Long concertId, Long gradeId, Integer quantity, String orderNo) {
                log.warn("[{}] 恢复库存降级: concertId={}, gradeId={}, quantity={}, orderNo={}", serviceName, concertId, gradeId, quantity, orderNo);
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }
        };
    }
}

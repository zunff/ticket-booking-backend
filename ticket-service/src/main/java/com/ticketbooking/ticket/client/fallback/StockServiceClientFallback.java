package com.ticketbooking.ticket.client.fallback;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.FeignFallbackException;
import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.common.sentinel.FeignFallbackFactory;
import com.ticketbooking.ticket.client.StockServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * StockServiceClient 降级处理 (ticket-service)
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
            public List<StockDTO> getStocksByConcertId(Long concertId) {
                log.warn("[{}] 批量查询库存降级: concertId={}", serviceName, concertId);
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }

            @Override
            public void initStock(Long concertId, Long gradeId, Integer totalStock) {
                log.warn("[{}] 初始化库存降级: concertId={}, gradeId={}", serviceName, concertId, gradeId);
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }

            @Override
            public void deleteByGradeIds(List<Long> gradeIds) {
                log.warn("[{}] 批量删除库存降级: gradeIds={}", serviceName, gradeIds);
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }

            @Override
            public void updateStock(Long concertId, Long gradeId, Integer newStock) {
                log.warn("[{}] 更新库存降级: concertId={}, gradeId={}", serviceName, concertId, gradeId);
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }
        };
    }
}

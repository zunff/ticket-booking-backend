package com.ticketbooking.stock.client.fallback;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.FeignFallbackException;
import com.ticketbooking.common.model.dto.TicketGradeDTO;
import com.ticketbooking.common.sentinel.FeignFallbackFactory;
import com.ticketbooking.stock.client.TicketServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TicketServiceClient 降级处理 (ticket-stock-service)
 */
@Slf4j
@Component
public class TicketServiceClientFallback extends FeignFallbackFactory<TicketServiceClient> {

    public TicketServiceClientFallback() {
        super("ticket-service");
    }

    @Override
    public TicketServiceClient create(Throwable cause) {
        logFallback(cause);
        return id -> {
            log.warn("[{}] 查询票档降级: id={}", serviceName, id);
            throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
        };
    }
}

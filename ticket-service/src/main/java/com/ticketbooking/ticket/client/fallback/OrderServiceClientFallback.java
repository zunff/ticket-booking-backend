package com.ticketbooking.ticket.client.fallback;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.FeignFallbackException;
import com.ticketbooking.common.model.dto.ConcertSalesDTO;
import com.ticketbooking.common.model.dto.DashboardStatsDTO;
import com.ticketbooking.common.model.dto.SalesDataDTO;
import com.ticketbooking.common.sentinel.FeignFallbackFactory;
import com.ticketbooking.ticket.client.OrderServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class OrderServiceClientFallback extends FeignFallbackFactory<OrderServiceClient> {

    public OrderServiceClientFallback() {
        super("ticket-order-service");
    }

    @Override
    public OrderServiceClient create(Throwable cause) {
        logFallback(cause);
        return new OrderServiceClient() {
            @Override
            public DashboardStatsDTO getDashboardStats() {
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }

            @Override
            public Integer countUserPurchased(Long userId, Long concertId) {
                log.warn("[{}] 查询用户购买数量降级: userId={}, concertId={}", serviceName, userId, concertId);
                // 降级时返回 0，允许继续下单
                return 0;
            }

            @Override
            public List<SalesDataDTO> getSalesData(Integer days) {
                log.warn("[{}] 获取销售数据降级: days={}", serviceName, days);
                return Collections.emptyList();
            }

            @Override
            public List<ConcertSalesDTO> getConcertSalesStats() {
                log.warn("[{}] 获取演唱会销售统计降级", serviceName);
                return Collections.emptyList();
            }
        };
    }
}

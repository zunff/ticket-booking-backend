package com.ticketbooking.stock.client.fallback;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.FeignFallbackException;
import com.ticketbooking.common.model.dto.OrderDTO;
import com.ticketbooking.common.model.qo.CreateOrderQO;
import com.ticketbooking.common.sentinel.FeignFallbackFactory;
import com.ticketbooking.stock.client.OrderServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OrderServiceClient 降级处理
 */
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
            public OrderDTO findByOrderNo(String orderNo) {
                log.warn("[{}] 查询订单降级: orderNo={}", serviceName, orderNo);
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }

            @Override
            public OrderDTO createOrder(CreateOrderQO qo) {
                log.warn("[{}] 创建订单降级: orderNo={}", serviceName, qo.getOrderNo());
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }

            @Override
            public Void markOrderFailed(String orderNo, String reason) {
                log.warn("[{}] 标记订单失败降级: orderNo={}, reason={}", serviceName, orderNo, reason);
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }

            @Override
            public Void markOrderPending(String orderNo) {
                log.warn("[{}] 标记订单待支付降级: orderNo={}", serviceName, orderNo);
                throw new FeignFallbackException(serviceName, ErrorCode.SERVICE_DEGRADED);
            }

            @Override
            public Boolean hasUserBought(Long userId, Long concertId, Long gradeId) {
                log.warn("[{}] 检查用户购买降级: userId={}, concertId={}, gradeId={}",
                         serviceName, userId, concertId, gradeId);
                // 降级时返回 false，允许继续下单（宁可多卖也不能误拒）
                return false;
            }

            @Override
            public Integer countUserPurchased(Long userId, Long concertId) {
                log.warn("[{}] 查询用户购买数量降级: userId={}, concertId={}", serviceName, userId, concertId);
                // 降级时返回 0，允许继续下单
                return 0;
            }
        };
    }
}

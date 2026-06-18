package com.ticketbooking.payment.client;

import com.ticketbooking.payment.client.fallback.OrderServiceClientFallback;
import com.ticketbooking.payment.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "ticket-order-service", path = "/order/internal",
             configuration = FeignClientConfig.class,
             fallbackFactory = OrderServiceClientFallback.class)
public interface OrderServiceClient {

    /**
     * 标记订单已支付（支付成功回调）
     */
    @PutMapping("/{orderNo}/paid")
    Void markOrderPaid(@PathVariable("orderNo") String orderNo);
}

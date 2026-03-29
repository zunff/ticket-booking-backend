package com.ticketbooking.stock.client;

import com.ticketbooking.common.model.dto.OrderDTO;
import com.ticketbooking.common.model.qo.CreateOrderQO;
import com.ticketbooking.stock.client.fallback.OrderServiceClientFallback;
import com.ticketbooking.stock.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "ticket-order-service", path = "/order/internal",
             configuration = FeignClientConfig.class,
             fallbackFactory = OrderServiceClientFallback.class)
public interface OrderServiceClient {

    @GetMapping("/{orderNo}")
    OrderDTO findByOrderNo(@PathVariable("orderNo") String orderNo);

    @PostMapping
    OrderDTO createOrder(@RequestBody CreateOrderQO qo);

    /**
     * 标记订单失败
     */
    @PutMapping("/{orderNo}/fail")
    Void markOrderFailed(@PathVariable("orderNo") String orderNo,
                         @RequestParam("reason") String reason);

    /**
     * 检查用户是否已购买
     */
    @GetMapping("/check-bought")
    Boolean hasUserBought(@RequestParam("userId") Long userId,
                          @RequestParam("concertId") Long concertId,
                          @RequestParam("gradeId") Long gradeId);

    /**
     * 查询用户在演唱会的已购买数量
     */
    @GetMapping("/count-purchased")
    Integer countUserPurchased(@RequestParam("userId") Long userId,
                               @RequestParam("concertId") Long concertId);
}

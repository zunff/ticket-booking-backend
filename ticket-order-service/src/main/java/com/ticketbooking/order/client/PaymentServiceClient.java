package com.ticketbooking.order.client;

import com.ticketbooking.common.model.dto.PayResponseDTO;
import com.ticketbooking.common.model.dto.RefundResultDTO;
import com.ticketbooking.common.model.dto.TradeQueryDTO;
import com.ticketbooking.common.model.qo.PayRequestQO;
import com.ticketbooking.common.model.qo.RefundRequestQO;
import com.ticketbooking.order.client.fallback.PaymentServiceClientFallback;
import com.ticketbooking.order.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "ticket-payment-service", path = "/payment/internal",
             configuration = FeignClientConfig.class,
             fallbackFactory = PaymentServiceClientFallback.class)
public interface PaymentServiceClient {

    @PostMapping("/prepay")
    PayResponseDTO prepay(@RequestBody PayRequestQO request);

    @GetMapping("/query/{orderNo}")
    TradeQueryDTO query(@PathVariable("orderNo") String orderNo,
                        @RequestParam("channel") String channel);

    @PostMapping("/close/{orderNo}")
    Boolean close(@PathVariable("orderNo") String orderNo,
                  @RequestParam("channel") String channel);

    @PostMapping("/refund")
    RefundResultDTO refund(@RequestBody RefundRequestQO request);
}

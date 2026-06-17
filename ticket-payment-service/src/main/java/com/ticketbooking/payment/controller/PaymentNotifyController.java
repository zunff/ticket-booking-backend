package com.ticketbooking.payment.controller;

import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.payment.model.dto.NotifyResultDTO;
import com.ticketbooking.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/notify")
@RequiredArgsConstructor
public class PaymentNotifyController {

    private final PaymentService paymentService;

    @PostMapping("/wechat")
    public void wechatNotify(HttpServletRequest request, HttpServletResponse response) throws IOException {
        NotifyResultDTO result = paymentService.handleNotify(PayChannel.WECHAT, request);
        String ack = paymentService.buildNotifyAck(PayChannel.WECHAT, result);
        writeResponse(response, ack);
    }

    @PostMapping("/alipay")
    public void alipayNotify(HttpServletRequest request, HttpServletResponse response) throws IOException {
        NotifyResultDTO result = paymentService.handleNotify(PayChannel.ALIPAY, request);
        String ack = paymentService.buildNotifyAck(PayChannel.ALIPAY, result);
        writeResponse(response, ack);
    }

    private void writeResponse(HttpServletResponse response, String content) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(content);
        response.getWriter().flush();
    }
}

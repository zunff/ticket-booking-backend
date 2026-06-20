package com.ticketbooking.payment.controller;

import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.PaymentStatus;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.payment.entity.PaymentRecord;
import com.ticketbooking.payment.service.PaymentRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;

/**
 * Mock 收银台 — 仅在 payment.mock.enabled=true 时装配，仅服务 {@code MOCK_PAGE_CONFIRM} 模式。
 * <p>
 * prepay 返回的 payUrl 指向本控制器，渲染极简收银台，用户手动点按钮确认支付结果。
 * quick-success / quick-fail 模式在 prepay 阶段已同步到达终态，不经过本控制器。
 */
@Slf4j
@Controller
@RequestMapping("/mock/cashier")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "true", matchIfMissing = true)
public class MockCashierController {

    private static final String CSS = """
            body{font-family:-apple-system,"Segoe UI",sans-serif;background:#f5f5f5;margin:0;padding:40px}
            .card{max-width:420px;margin:40px auto;background:#fff;border-radius:12px;padding:32px;box-shadow:0 2px 12px rgba(0,0,0,.08);text-align:center}
            .tag{color:#999;font-size:13px;margin-bottom:8px}
            .info{text-align:left;margin:24px 0}
            .row{display:flex;justify-content:space-between;padding:10px 0;border-bottom:1px solid #f0f0f0;font-size:14px}
            .row span{color:#999}
            .row.amount b{color:#ff4d4f;font-size:18px}
            p{font-size:15px;color:#333;line-height:1.6}
            button{width:100%;padding:12px;margin-top:12px;border:none;border-radius:8px;font-size:16px;cursor:pointer}
            .ok{background:#07c160;color:#fff}
            .fail{background:#fff;color:#ff4d4f;border:1px solid #ff4d4f}
            """;

    private static final String PAGE = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>{{TITLE}}</title>
              <style>{{CSS}}</style>
            </head>
            <body>
              <div class="card">
                <div class="tag">Mock 支付收银台（仅测试）</div>
                {{BODY}}
              </div>
            </body>
            </html>
            """;

    private static final String INFO = """
            <div class="info">
              <div class="row"><span>订单号</span><b>{{TRADE_NO}}</b></div>
              <div class="row"><span>商品</span><b>{{SUBJECT}}</b></div>
              <div class="row amount"><span>支付金额</span><b>¥{{AMOUNT}}</b></div>
            </div>
            """;

    // action 不能写成裸 "success"/"fail"：收银台页面 URL 无尾斜杠（/mock/cashier/{outTradeNo}），
    // 相对解析会把 {outTradeNo} 段替换掉，导致 POST 飘到 /mock/cashier/success 命中 GET handler 而 405。
    // 带上 {{TRADE_NO}} 让相对解析拼成正确的 /mock/cashier/{outTradeNo}/success。
    private static final String CONFIRM_BUTTONS = """
            <form method="post" action="{{TRADE_NO}}/success"><button class="ok" type="submit">模拟支付成功</button></form>
            <form method="post" action="{{TRADE_NO}}/fail"><button class="fail" type="submit">模拟支付失败</button></form>
            """;

    private final PaymentRecordService recordService;

    @GetMapping(value = "/{outTradeNo}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String cashier(@PathVariable String outTradeNo) {
        PaymentRecord record = requireRecord(outTradeNo);
        int status = record.getStatus();

        if (status == PaymentStatus.SUCCESS.getCode()) {
            return renderPage("支付结果", "<p>该订单已支付成功，无需重复支付。</p>");
        }
        if (status != PaymentStatus.PENDING.getCode() && status != PaymentStatus.PROCESSING.getCode()) {
            return renderPage("支付结果", "<p>订单状态：" + escape(PaymentStatus.of(status).getDesc()) + "，无法继续支付。</p>");
        }

        return renderPage("模拟收银台", info(record) + CONFIRM_BUTTONS.replace("{{TRADE_NO}}", escape(outTradeNo)));
    }

    @PostMapping(value = "/{outTradeNo}/success", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String paySuccess(@PathVariable String outTradeNo) {
        PaymentRecord record = requireRecord(outTradeNo);
        if (record.getStatus() == PaymentStatus.SUCCESS.getCode()) {
            return renderPage("支付结果", "<p>该订单已支付成功。</p>");
        }
        recordService.updateOnNotifySuccess(
                outTradeNo,
                record.getAmount(),
                record.getChannelTradeNo(),
                LocalDateTime.now()
        );
        log.info("[mock] pay success: outTradeNo={}", outTradeNo);
        return renderPage("支付结果", "<p>支付成功！订单号：" + escape(outTradeNo) + "</p>");
    }

    @PostMapping(value = "/{outTradeNo}/fail", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String payFail(@PathVariable String outTradeNo) {
        requireRecord(outTradeNo);
        recordService.updateStatus(outTradeNo, PaymentStatus.FAILED);
        log.info("[mock] pay fail: outTradeNo={}", outTradeNo);
        return renderPage("支付结果", "<p>支付失败（模拟）。订单号：" + escape(outTradeNo) + "</p>");
    }

    // ======================== 渲染辅助 ========================

    private String info(PaymentRecord record) {
        return INFO
                .replace("{{TRADE_NO}}", escape(record.getOutTradeNo()))
                .replace("{{SUBJECT}}", escape(record.getSubject()))
                .replace("{{AMOUNT}}", formatAmount(record.getAmount()));
    }

    private String renderPage(String title, String body) {
        // 顺序：先替换静态槽（TITLE/CSS），最后插入 BODY，避免 body 内容误触发其它槽
        return PAGE
                .replace("{{TITLE}}", escape(title))
                .replace("{{CSS}}", CSS)
                .replace("{{BODY}}", body);
    }

    private PaymentRecord requireRecord(String outTradeNo) {
        PaymentRecord record = recordService.findByOutTradeNo(outTradeNo);
        if (record == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return record;
    }

    private String formatAmount(Integer fen) {
        return String.format("%.2f", fen / 100.0);
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

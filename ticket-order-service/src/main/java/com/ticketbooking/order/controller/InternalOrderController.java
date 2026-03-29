package com.ticketbooking.order.controller;

import com.ticketbooking.common.model.dto.ConcertSalesDTO;
import com.ticketbooking.common.model.dto.DashboardStatsDTO;
import com.ticketbooking.common.model.dto.OrderDTO;
import com.ticketbooking.common.model.dto.SalesDataDTO;
import com.ticketbooking.common.model.qo.CreateOrderQO;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    @GetMapping("/{orderNo}")
    public Result<OrderDTO> findByOrderNo(@PathVariable String orderNo) {
        return Result.success(orderService.findDTOByOrderNo(orderNo));
    }

    @PostMapping
    public Result<OrderDTO> createOrder(@RequestBody CreateOrderQO qo) {
        return Result.success(orderService.createOrderDTO(qo));
    }

    /**
     * 标记订单失败
     */
    @PutMapping("/{orderNo}/fail")
    public Result<Void> markOrderFailed(@PathVariable String orderNo,
                                        @RequestParam String reason) {
        orderService.markOrderFailed(orderNo, reason);
        return Result.success();
    }

    /**
     * 标记订单已支付
     */
    @PutMapping("/{orderNo}/paid")
    public Result<Void> markOrderPaid(@PathVariable String orderNo) {
        orderService.markOrderPaid(orderNo);
        return Result.success();
    }

    /**
     * 检查用户是否已购买
     */
    @GetMapping("/check-bought")
    public Result<Boolean> hasUserBought(@RequestParam Long userId,
                                         @RequestParam Long concertId,
                                         @RequestParam Long gradeId) {
        return Result.success(orderService.hasUserBought(userId, concertId, gradeId));
    }

    /**
     * 查询用户在演唱会的已购买数量
     */
    @GetMapping("/count-purchased")
    public Result<Integer> countUserPurchased(@RequestParam Long userId,
                                               @RequestParam Long concertId) {
        return Result.success(orderService.countUserPurchased(userId, concertId));
    }

    /**
     * 获取仪表盘统计数据
     */
    @GetMapping("/dashboard-stats")
    public Result<DashboardStatsDTO> getDashboardStats() {
        return Result.success(orderService.getDashboardStats());
    }

    /**
     * 获取最近N天的销售数据
     */
    @GetMapping("/sales-data")
    public Result<List<SalesDataDTO>> getSalesData(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(orderService.getSalesData(days));
    }

    /**
     * 获取各演唱会的销售统计
     */
    @GetMapping("/concert-sales-stats")
    public Result<List<ConcertSalesDTO>> getConcertSalesStats() {
        return Result.success(orderService.getConcertSalesStats());
    }
}

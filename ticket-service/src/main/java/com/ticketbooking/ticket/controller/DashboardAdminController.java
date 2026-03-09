package com.ticketbooking.ticket.controller;

import com.ticketbooking.common.annotation.RequireAdmin;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.ticket.model.vo.ConcertSalesStatsVO;
import com.ticketbooking.ticket.model.vo.DashboardStatsVO;
import com.ticketbooking.ticket.model.vo.SalesDataPointVO;
import com.ticketbooking.ticket.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员仪表盘控制器
 */
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardAdminController {

    private final DashboardService dashboardService;

    /**
     * 获取仪表盘统计数据
     */
    @GetMapping
    @RequireAdmin
    public Result<DashboardStatsVO> getDashboardStats() {
        DashboardStatsVO stats = dashboardService.getDashboardStats();
        return Result.success(stats);
    }

    /**
     * 获取销售数据（用于图表）
     * @param days 天数
     */
    @GetMapping("/sales")
    @RequireAdmin
    public Result<List<SalesDataPointVO>> getSalesData(
            @RequestParam(defaultValue = "30") Integer days) {
        List<SalesDataPointVO> salesData = dashboardService.getSalesData(days);
        return Result.success(salesData);
    }

    /**
     * 获取演唱会销售统计
     */
    @GetMapping("/concerts")
    @RequireAdmin
    public Result<List<ConcertSalesStatsVO>> getConcertSalesStats() {
        List<ConcertSalesStatsVO> concertStats = dashboardService.getConcertSalesStats();
        return Result.success(concertStats);
    }
}

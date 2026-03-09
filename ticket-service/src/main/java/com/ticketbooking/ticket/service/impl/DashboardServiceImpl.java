package com.ticketbooking.ticket.service.impl;

import com.ticketbooking.ticket.model.vo.ConcertSalesStatsVO;
import com.ticketbooking.ticket.model.vo.DashboardStatsVO;
import com.ticketbooking.ticket.model.vo.SalesDataPointVO;
import com.ticketbooking.ticket.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 仪表盘服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    // TODO: 注入 OrderServiceClient 来获取订单数据

    @Override
    public DashboardStatsVO getDashboardStats() {
        DashboardStatsVO stats = new DashboardStatsVO();

        // 暂时返回默认值，待接入订单服务后更新
        stats.setTotalConcerts(0);
        stats.setOnSaleConcerts(0);
        stats.setTotalOrders(0);
        stats.setTotalRevenue(0L);
        stats.setTodayOrders(0);
        stats.setTodayRevenue(0L);

        return stats;
    }

    @Override
    public List<SalesDataPointVO> getSalesData(Integer days) {
        List<SalesDataPointVO> salesData = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 生成最近N天的数据（占位符）
        for (int i = days - 1; i >= 0; i--) {
            SalesDataPointVO point = new SalesDataPointVO();
            point.setDate(today.minusDays(i).format(formatter));
            point.setOrders(0);
            point.setRevenue(0L);
            salesData.add(point);
        }

        return salesData;
    }

    @Override
    public List<ConcertSalesStatsVO> getConcertSalesStats() {
        // 暂时返回空列表
        return new ArrayList<>();
    }
}

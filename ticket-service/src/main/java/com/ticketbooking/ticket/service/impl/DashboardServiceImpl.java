package com.ticketbooking.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticketbooking.common.enums.ConcertStatus;
import com.ticketbooking.common.model.dto.DashboardStatsDTO;
import com.ticketbooking.ticket.client.OrderServiceClient;
import com.ticketbooking.ticket.entity.Concert;
import com.ticketbooking.ticket.mapper.ConcertMapper;
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

    private final ConcertMapper concertMapper;
    private final OrderServiceClient orderServiceClient;

    @Override
    public DashboardStatsVO getDashboardStats() {
        DashboardStatsVO stats = new DashboardStatsVO();

        // 获取演唱会统计
        long totalConcerts = concertMapper.selectCount(null);
        long onSaleConcerts = concertMapper.selectCount(
                new LambdaQueryWrapper<Concert>()
                        .ne(Concert::getStatus, ConcertStatus.CLOSED.getCode())
                        .le(Concert::getStartSaleTime, LocalDateTime.now())
                        .gt(Concert::getEndSaleTime, LocalDateTime.now())
        );

        stats.setTotalConcerts((int) totalConcerts);
        stats.setOnSaleConcerts((int) onSaleConcerts);

        // 获取订单统计（从订单服务）
        try {
            log.debug("Calling order service for dashboard stats...");
            DashboardStatsDTO orderStats = orderServiceClient.getDashboardStats();
            log.debug("Order stats received: {}", orderStats);
            if (orderStats != null) {
                stats.setTotalOrders(orderStats.getTotalOrders());
                stats.setTotalRevenue(orderStats.getTotalRevenue());
                stats.setTodayOrders(orderStats.getTodayOrders());
                stats.setTodayRevenue(orderStats.getTodayRevenue());
            } else {
                log.warn("Order stats is null, using defaults");
                setDefaultOrderStats(stats);
            }
        } catch (Exception e) {
            log.error("Failed to get order stats: {}", e.getMessage(), e);
            setDefaultOrderStats(stats);
        }

        return stats;
    }

    private void setDefaultOrderStats(DashboardStatsVO stats) {
        stats.setTotalOrders(0);
        stats.setTotalRevenue(0L);
        stats.setTodayOrders(0);
        stats.setTodayRevenue(0L);
    }

    @Override
    public List<SalesDataPointVO> getSalesData(Integer days) {
        List<SalesDataPointVO> salesData = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 生成最近N天的数据（占位符）
        // TODO: 从订单服务获取真实数据
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
        // TODO: 从订单服务获取真实数据
        return new ArrayList<>();
    }
}

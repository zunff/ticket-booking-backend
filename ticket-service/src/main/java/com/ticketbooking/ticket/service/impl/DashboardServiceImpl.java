package com.ticketbooking.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticketbooking.common.enums.ConcertStatus;
import com.ticketbooking.common.model.dto.ConcertSalesDTO;
import com.ticketbooking.common.model.dto.DashboardStatsDTO;
import com.ticketbooking.common.model.dto.SalesDataDTO;
import com.ticketbooking.ticket.client.OrderServiceClient;
import com.ticketbooking.ticket.entity.Concert;
import com.ticketbooking.ticket.entity.TicketGrade;
import com.ticketbooking.ticket.mapper.ConcertMapper;
import com.ticketbooking.ticket.mapper.TicketGradeMapper;
import com.ticketbooking.ticket.model.vo.ConcertSalesStatsVO;
import com.ticketbooking.ticket.model.vo.DashboardStatsVO;
import com.ticketbooking.ticket.model.vo.SalesDataPointVO;
import com.ticketbooking.ticket.service.DashboardService;
import com.ticketbooking.ticket.service.TicketGradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 仪表盘服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final ConcertMapper concertMapper;
    private final TicketGradeService ticketGradeService;
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

        try {
            log.debug("Calling order service for sales data, days={}", days);
            List<SalesDataDTO> orderSalesData = orderServiceClient.getSalesData(days);

            if (orderSalesData != null && !orderSalesData.isEmpty()) {
                for (SalesDataDTO dto : orderSalesData) {
                    SalesDataPointVO point = new SalesDataPointVO();
                    point.setDate(dto.getDate());
                    point.setOrders(dto.getOrders());
                    point.setRevenue(dto.getRevenue());
                    salesData.add(point);
                }
                log.debug("Sales data received: {} points", salesData.size());
                return salesData;
            }
        } catch (Exception e) {
            log.error("Failed to get sales data: {}", e.getMessage(), e);
        }

        // 降级：返回空数据
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
        try {
            log.debug("Calling order service for concert sales stats");
            List<ConcertSalesDTO> concertSalesList = orderServiceClient.getConcertSalesStats();

            if (concertSalesList == null || concertSalesList.isEmpty()) {
                return new ArrayList<>();
            }

            // 获取演唱会名称
            List<Long> concertIds = concertSalesList.stream()
                    .map(ConcertSalesDTO::getConcertId)
                    .collect(Collectors.toList());

            List<Concert> concerts = concertMapper.selectBatchIds(concertIds);
            Map<Long, Concert> concertMap = concerts.stream()
                    .collect(Collectors.toMap(Concert::getId, Function.identity()));

            // 获取各演唱会的总库存（按档位聚合）
            List<TicketGrade> grades = ticketGradeService.lambdaQuery().in(TicketGrade::getConcertId, concertIds).list();
            Map<Long, Integer> concertTotalStockMap = grades.stream()
                    .collect(Collectors.groupingBy(
                            TicketGrade::getConcertId,
                            Collectors.summingInt(g -> g.getTotalStock() != null ? g.getTotalStock() : 0)
                    ));

            // 转换为 VO 并填充演唱会名称
            List<ConcertSalesStatsVO> result = new ArrayList<>();
            for (ConcertSalesDTO dto : concertSalesList) {
                ConcertSalesStatsVO vo = new ConcertSalesStatsVO();
                vo.setConcertId(dto.getConcertId());
                vo.setTotalOrders(dto.getTotalOrders());
                vo.setTotalTickets(dto.getTotalTickets());
                vo.setTotalRevenue(dto.getTotalRevenue());

                Concert concert = concertMap.get(dto.getConcertId());
                if (concert != null) {
                    vo.setConcertName(concert.getName());
                    // 计算完成率（使用档位总库存）
                    Integer totalStock = concertTotalStockMap.getOrDefault(dto.getConcertId(), 0);
                    if (totalStock > 0) {
                        vo.setCompletionRate(dto.getTotalTickets() * 100.0 / totalStock);
                    } else {
                        vo.setCompletionRate(0.0);
                    }
                }

                result.add(vo);
            }

            log.debug("Concert sales stats received: {} concerts", result.size());
            return result;
        } catch (Exception e) {
            log.error("Failed to get concert sales stats: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}

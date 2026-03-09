package com.ticketbooking.ticket.service;

import com.ticketbooking.ticket.model.vo.ConcertSalesStatsVO;
import com.ticketbooking.ticket.model.vo.DashboardStatsVO;
import com.ticketbooking.ticket.model.vo.SalesDataPointVO;

import java.util.List;

/**
 * 仪表盘服务接口
 */
public interface DashboardService {

    /**
     * 获取仪表盘统计数据
     */
    DashboardStatsVO getDashboardStats();

    /**
     * 获取销售数据（用于图表）
     * @param days 天数
     */
    List<SalesDataPointVO> getSalesData(Integer days);

    /**
     * 获取演唱会销售统计
     */
    List<ConcertSalesStatsVO> getConcertSalesStats();
}

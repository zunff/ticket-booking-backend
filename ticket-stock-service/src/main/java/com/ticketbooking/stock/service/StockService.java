package com.ticketbooking.stock.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.stock.entity.Stock;
import com.ticketbooking.stock.entity.StockLog;

import java.util.List;

public interface StockService extends IService<Stock> {
    
    int decrementStock(Long concertId, Long gradeId, Integer quantity, String orderNo);
    
    int incrementStock(Long concertId, Long gradeId, Integer quantity, String orderNo);
    
    Stock getStockByConcertAndGrade(Long concertId, Long gradeId);
    
    Integer getAvailableStock(Long concertId, Long gradeId);
    
    void syncStockToRedis(Long concertId, Long gradeId);
    
    List<StockLog> getStockLogs(Long concertId, Long gradeId);
    
    void adjustStock(Long concertId, Long gradeId, Integer newStock, String remark);
}

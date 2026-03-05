package com.ticketbooking.stock.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.stock.entity.StockLog;
import com.ticketbooking.stock.entity.Ticket;

import java.util.List;

public interface StockService extends IService<Ticket> {
    
    int decrementStock(Long ticketId, Integer quantity, String orderNo);
    
    int incrementStock(Long ticketId, Integer quantity, String orderNo);
    
    Ticket getTicketById(Long ticketId);
    
    Integer getAvailableStock(Long ticketId);
    
    void syncStockToRedis(Long ticketId);
    
    List<StockLog> getStockLogs(Long ticketId);
    
    void adjustStock(Long ticketId, Integer newStock, String remark);
}

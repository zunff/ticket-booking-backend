package com.ticketbooking.stock.controller;

import com.ticketbooking.common.annotation.RequireAdmin;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.stock.entity.StockLog;
import com.ticketbooking.stock.entity.Ticket;
import com.ticketbooking.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/stock")
@RequiredArgsConstructor
public class StockAdminController {
    
    private final StockService stockService;
    
    @GetMapping("/{ticketId}")
    @RequireAdmin
    public Result<Ticket> getTicketInfo(@PathVariable Long ticketId) {
        Ticket ticket = stockService.getTicketById(ticketId);
        return ticket != null ? Result.success(ticket) : Result.error(2001, "票务不存在");
    }
    
    @GetMapping("/logs/{ticketId}")
    @RequireAdmin
    public Result<List<StockLog>> getStockLogs(@PathVariable Long ticketId) {
        return Result.success(stockService.getStockLogs(ticketId));
    }
    
    @PostMapping("/adjust")
    @RequireAdmin
    public Result<String> adjustStock(
            @RequestParam Long ticketId,
            @RequestParam Integer newStock,
            @RequestParam(required = false, defaultValue = "管理员调整") String remark) {
        stockService.adjustStock(ticketId, newStock, remark);
        return Result.success("库存调整成功");
    }
}

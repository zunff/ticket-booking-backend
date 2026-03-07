package com.ticketbooking.stock.controller;

import com.ticketbooking.common.annotation.RequireAdmin;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.stock.entity.StockLog;
import com.ticketbooking.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/stock")
@RequiredArgsConstructor
public class StockAdminController {
    
    private final StockService stockService;
    
    @GetMapping("/logs/{concertId}/{gradeId}")
    @RequireAdmin
    public Result<List<StockLog>> getStockLogs(@PathVariable Long concertId, @PathVariable Long gradeId) {
        return Result.success(stockService.getStockLogs(concertId, gradeId));
    }
    
    @PostMapping("/adjust")
    @RequireAdmin
    public Result<String> adjustStock(
            @RequestParam Long concertId,
            @RequestParam Long gradeId,
            @RequestParam Integer newStock,
            @RequestParam(required = false, defaultValue = "管理员调整") String remark) {
        stockService.adjustStock(concertId, gradeId, newStock, remark);
        return Result.success("库存调整成功");
    }
}

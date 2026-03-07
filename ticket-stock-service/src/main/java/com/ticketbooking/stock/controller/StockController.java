package com.ticketbooking.stock.controller;

import com.ticketbooking.common.result.Result;
import com.ticketbooking.stock.entity.StockLog;
import com.ticketbooking.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stock")
@RequiredArgsConstructor
public class StockController {
    
    private final StockService stockService;
    
    @GetMapping("/{concertId}/{gradeId}")
    public Result<Integer> getStock(@PathVariable Long concertId, @PathVariable Long gradeId) {
        Integer stock = stockService.getAvailableStock(concertId, gradeId);
        return stock != null ? Result.success(stock) : Result.error(2001, "库存不存在");
    }
    
    @PostMapping("/sync/{concertId}/{gradeId}")
    public Result<String> syncStock(@PathVariable Long concertId, @PathVariable Long gradeId) {
        stockService.syncStockToRedis(concertId, gradeId);
        return Result.success("库存同步成功");
    }
    
    @GetMapping("/logs/{concertId}/{gradeId}")
    public Result<List<StockLog>> getStockLogs(@PathVariable Long concertId, @PathVariable Long gradeId) {
        List<StockLog> logs = stockService.getStockLogs(concertId, gradeId);
        return Result.success(logs);
    }
}

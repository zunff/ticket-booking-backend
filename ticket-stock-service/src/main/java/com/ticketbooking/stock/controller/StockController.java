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
        return Result.success(stock);
    }

    @GetMapping("/logs/{concertId}/{gradeId}")
    public Result<List<StockLog>> getStockLogs(@PathVariable Long concertId, @PathVariable Long gradeId) {
        List<StockLog> logs = stockService.getStockLogs(concertId, gradeId);
        return Result.success(logs);
    }
}

package com.ticketbooking.stock.controller;

import com.ticketbooking.common.result.Result;
import com.ticketbooking.stock.entity.StockLog;
import com.ticketbooking.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping("/{concertId}")
    public Result<Map<Long, Integer>> getStocksByConcertId(@PathVariable Long concertId) {
        return Result.success(stockService.getStockMapByConcertId(concertId));
    }

    @GetMapping("/logs/{concertId}/{gradeId}")
    public Result<List<StockLog>> getStockLogs(@PathVariable Long concertId, @PathVariable Long gradeId) {
        List<StockLog> logs = stockService.getStockLogs(concertId, gradeId);
        return Result.success(logs);
    }
}

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
    
    @GetMapping("/{ticketId}")
    public Result<Integer> getStock(@PathVariable Long ticketId) {
        Integer stock = stockService.getAvailableStock(ticketId);
        return stock != null ? Result.success(stock) : Result.error(2001, "票务不存在");
    }
    
    @PostMapping("/sync/{ticketId}")
    public Result<String> syncStock(@PathVariable Long ticketId) {
        stockService.syncStockToRedis(ticketId);
        return Result.success("库存同步成功");
    }
}

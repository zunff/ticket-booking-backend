package com.ticketbooking.stock.controller;

import com.ticketbooking.common.result.Result;
import com.ticketbooking.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/stock")
@RequiredArgsConstructor
public class InternalStockController {
    
    private final StockService stockService;
    
    @PostMapping("/decrement")
    public Result<Integer> decrementStock(
            @RequestParam Long concertId,
            @RequestParam Long gradeId,
            @RequestParam Integer quantity,
            @RequestParam String orderNo) {
        int result = stockService.decrementStock(concertId, gradeId, quantity, orderNo);
        return result > 0 ? Result.success(result) : Result.error(3001, "库存扣减失败");
    }
    
    @PostMapping("/increment")
    public Result<Integer> incrementStock(
            @RequestParam Long concertId,
            @RequestParam Long gradeId,
            @RequestParam Integer quantity,
            @RequestParam String orderNo) {
        int result = stockService.incrementStock(concertId, gradeId, quantity, orderNo);
        return result > 0 ? Result.success(result) : Result.error(3002, "库存回滚失败");
    }
}

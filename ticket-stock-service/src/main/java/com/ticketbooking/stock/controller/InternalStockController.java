package com.ticketbooking.stock.controller;

import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/stock")
@RequiredArgsConstructor
public class InternalStockController {

    private final StockService stockService;

    @GetMapping
    public Result<StockDTO> getStock(@RequestParam Long concertId, @RequestParam Long gradeId) {
        return Result.success(stockService.getStockDTO(concertId, gradeId));
    }

    @GetMapping("/batch/{concertId}")
    public Result<List<StockDTO>> getStocksByConcertId(@PathVariable Long concertId) {
        return Result.success(stockService.getStockDTOsByConcertId(concertId));
    }

    @PostMapping("/init")
    public Result<Void> initStock(
            @RequestParam Long concertId,
            @RequestParam Long gradeId,
            @RequestParam Integer totalStock) {
        stockService.initStock(concertId, gradeId, totalStock);
        return Result.success();
    }
}

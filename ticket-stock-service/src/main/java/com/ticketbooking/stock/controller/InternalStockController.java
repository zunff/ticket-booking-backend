package com.ticketbooking.stock.controller;

import com.ticketbooking.common.model.dto.StockDTO;
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
    public StockDTO getStock(@RequestParam Long concertId, @RequestParam Long gradeId) {
        return stockService.getStockDTO(concertId, gradeId);
    }

    @GetMapping("/batch/{concertId}")
    public List<StockDTO> getStocksByConcertId(@PathVariable Long concertId) {
        return stockService.getStockDTOsByConcertId(concertId);
    }

    @PostMapping("/decrement")
    public Integer decrementStock(
            @RequestParam Long concertId,
            @RequestParam Long gradeId,
            @RequestParam Integer quantity,
            @RequestParam String orderNo) {
        return stockService.decrementStock(concertId, gradeId, quantity, orderNo);
    }

    @PostMapping("/increment")
    public Integer incrementStock(
            @RequestParam Long concertId,
            @RequestParam Long gradeId,
            @RequestParam Integer quantity,
            @RequestParam String orderNo) {
        return stockService.incrementStock(concertId, gradeId, quantity, orderNo);
    }

    @PostMapping("/init")
    public void initStock(
            @RequestParam Long concertId,
            @RequestParam Long gradeId,
            @RequestParam Integer totalStock) {
        stockService.initStock(concertId, gradeId, totalStock);
    }
}

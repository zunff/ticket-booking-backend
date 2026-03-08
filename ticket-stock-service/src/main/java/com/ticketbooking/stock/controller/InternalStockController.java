package com.ticketbooking.stock.controller;

import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.common.model.dto.TicketGradeDTO;
import com.ticketbooking.stock.client.TicketServiceClient;
import com.ticketbooking.stock.entity.Stock;
import com.ticketbooking.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal/stock")
@RequiredArgsConstructor
public class InternalStockController {

    private final StockService stockService;
    private final TicketServiceClient ticketServiceClient;

    @GetMapping
    public StockDTO getStock(@RequestParam Long concertId, @RequestParam Long gradeId) {
        Stock stock = stockService.getStockByConcertAndGrade(concertId, gradeId);
        if (stock == null) {
            return null;
        }

        TicketGradeDTO grade = ticketServiceClient.getGradeById(gradeId);
        if (grade == null) {
            return null;
        }

        StockDTO dto = new StockDTO();
        dto.setId(stock.getId());
        dto.setConcertId(concertId);
        dto.setConcertName(grade.getConcertName());
        dto.setGradeId(gradeId);
        dto.setGradeName(grade.getGradeName());
        dto.setPrice(grade.getPrice());
        dto.setAvailableStock(stock.getAvailableStock());

        return dto;
    }

    @GetMapping("/batch/{concertId}")
    public List<StockDTO> getStocksByConcertId(@PathVariable Long concertId) {
        List<Stock> stocks = stockService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Stock>()
                        .eq(Stock::getConcertId, concertId)
        );

        return stocks.stream()
                .map(stock -> {
                    StockDTO dto = new StockDTO();
                    dto.setId(stock.getId());
                    dto.setConcertId(stock.getConcertId());
                    dto.setGradeId(stock.getGradeId());
                    dto.setAvailableStock(stock.getAvailableStock());
                    return dto;
                })
                .collect(Collectors.toList());
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
}

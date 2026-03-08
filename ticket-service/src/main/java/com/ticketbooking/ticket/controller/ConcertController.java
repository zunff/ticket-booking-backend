package com.ticketbooking.ticket.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.ticket.client.StockServiceClient;
import com.ticketbooking.ticket.converter.ConcertConverter;
import com.ticketbooking.ticket.entity.Concert;
import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.ticket.entity.TicketGrade;
import com.ticketbooking.ticket.model.vo.ConcertDetailWithStockVO;
import com.ticketbooking.ticket.model.vo.ConcertVO;
import com.ticketbooking.ticket.service.ConcertService;
import com.ticketbooking.ticket.service.TicketGradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/concerts")
@RequiredArgsConstructor
public class ConcertController {
    
    private final ConcertService concertService;
    private final TicketGradeService ticketGradeService;
    private final ConcertConverter concertConverter;
    private final StockServiceClient stockServiceClient;
    
    @GetMapping
    public Result<PageResult<ConcertVO>> getConcerts(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size) {
        Page<Concert> page = concertService.page(new Page<>(current, size));
        PageResult<ConcertVO> result = PageResult.of(
                concertConverter.toVOList(page.getRecords()),
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
        return Result.success(result);
    }
    
    @GetMapping("/on-sale")
    public Result<PageResult<ConcertVO>> getOnSaleConcerts(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size) {
        Page<Concert> page = concertService.getOnSaleConcerts(new Page<>(current, size));
        PageResult<ConcertVO> result = PageResult.of(
                concertConverter.toVOList(page.getRecords()),
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
        return Result.success(result);
    }
    
    @GetMapping("/{id}")
    public Result<ConcertDetailWithStockVO> getConcertDetail(@PathVariable Long id) {
        Concert concert = concertService.getById(id);
        if (concert == null) {
            return Result.error(2001, "演唱会不存在");
        }
        
        List<TicketGrade> grades = ticketGradeService.getGradesByConcertId(id);

        List<StockDTO> stocks = stockServiceClient.getStocksByConcertId(id);
        Map<Long, Integer> stockMap = stocks.stream()
                .collect(Collectors.toMap(StockDTO::getGradeId, StockDTO::getAvailableStock));
        
        ConcertDetailWithStockVO result = concertConverter.toDetailWithStockVO(concert, grades, stockMap);
        return Result.success(result);
    }
}

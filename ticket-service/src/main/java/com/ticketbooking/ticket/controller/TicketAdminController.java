package com.ticketbooking.ticket.controller;

import com.ticketbooking.common.annotation.RequireAdmin;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.ticket.entity.Ticket;
import com.ticketbooking.ticket.service.TicketService;
import com.ticketbooking.ticket.model.vo.TicketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/tickets")
@RequiredArgsConstructor
public class TicketAdminController {
    
    private final TicketService ticketService;
    
    @PostMapping
    @RequireAdmin
    public Result<TicketVO> createTicket(@RequestBody Ticket ticket) {
        return Result.success("票务创建成功", convertToVO(ticketService.createTicket(ticket)));
    }
    
    @PutMapping("/{id}")
    @RequireAdmin
    public Result<TicketVO> updateTicket(@PathVariable Long id, @RequestBody Ticket ticket) {
        ticket.setId(id);
        return Result.success("票务更新成功", convertToVO(ticketService.updateTicket(ticket)));
    }
    
    @GetMapping
    @RequireAdmin
    public Result<List<TicketVO>> getAllTickets() {
        List<TicketVO> vos = ticketService.getAllTickets().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(vos);
    }
    
    @GetMapping("/{id}")
    @RequireAdmin
    public Result<TicketVO> getTicketById(@PathVariable Long id) {
        Ticket ticket = ticketService.getTicketById(id);
        return ticket != null ? Result.success(convertToVO(ticket)) : Result.error(2001, "票务不存在");
    }
    
    @PostMapping("/{id}/start-sale")
    @RequireAdmin
    public Result<String> startSale(@PathVariable Long id) {
        ticketService.startSale(id);
        return Result.success("开售成功");
    }
    
    @PostMapping("/{id}/stop-sale")
    @RequireAdmin
    public Result<String> stopSale(@PathVariable Long id) {
        ticketService.stopSale(id);
        return Result.success("停售成功");
    }
    
    @DeleteMapping("/{id}")
    @RequireAdmin
    public Result<String> deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
        return Result.success("删除成功");
    }
    
    @PostMapping("/{id}/sync-stock")
    @RequireAdmin
    public Result<String> syncStock(@PathVariable Long id) {
        ticketService.syncStockToRedis(id);
        return Result.success("库存同步成功");
    }
    
    private TicketVO convertToVO(Ticket ticket) {
        TicketVO vo = new TicketVO();
        BeanUtils.copyProperties(ticket, vo);
        return vo;
    }
}

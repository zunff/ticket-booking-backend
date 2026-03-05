package com.ticketbooking.ticket.controller;

import com.ticketbooking.common.annotation.RequireAuth;
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
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {
    
    private final TicketService ticketService;
    
    @GetMapping
    public Result<List<TicketVO>> getAllTickets() {
        List<Ticket> tickets = ticketService.getAllTickets();
        List<TicketVO> vos = tickets.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(vos);
    }
    
    @GetMapping("/available")
    public Result<List<TicketVO>> getAvailableTickets() {
        List<Ticket> tickets = ticketService.getAvailableTickets();
        List<TicketVO> vos = tickets.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(vos);
    }
    
    @GetMapping("/{id}")
    public Result<TicketVO> getTicketById(@PathVariable Long id) {
        Ticket ticket = ticketService.getTicketById(id);
        return ticket != null ? Result.success(convertToVO(ticket)) : Result.error(2001, "票务不存在");
    }
    
    private TicketVO convertToVO(Ticket ticket) {
        TicketVO vo = new TicketVO();
        BeanUtils.copyProperties(ticket, vo);
        return vo;
    }
}

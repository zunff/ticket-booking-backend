package com.ticketbooking.ticket.controller;

import com.ticketbooking.common.annotation.RequireAuth;
import com.ticketbooking.common.context.UserContext;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.ticket.entity.Ticket;
import com.ticketbooking.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {
    
    private final TicketService ticketService;
    
    @PostMapping
    @RequireAuth
    public Result<Ticket> createTicket(@RequestBody Ticket ticket) {
        return Result.success("票务创建成功", ticketService.createTicket(ticket));
    }
    
    @GetMapping
    public Result<List<Ticket>> getAllTickets() {
        return Result.success(ticketService.getAllTickets());
    }
    
    @GetMapping("/available")
    public Result<List<Ticket>> getAvailableTickets() {
        return Result.success(ticketService.getAvailableTickets());
    }
    
    @GetMapping("/{id}")
    public Result<Ticket> getTicketById(@PathVariable Long id) {
        Ticket ticket = ticketService.getTicketById(id);
        return ticket != null ? Result.success(ticket) : Result.error(2001, "票务不存在");
    }
    
    @PostMapping("/book")
    @RequireAuth
    public Result<String> bookTicket(
            @RequestParam Long ticketId,
            @RequestParam(defaultValue = "1") Integer quantity) {
        Long userId = UserContext.getUserId();
        String orderNo = ticketService.bookTicket(ticketId, userId, quantity);
        return Result.success("抢票成功", orderNo);
    }
}

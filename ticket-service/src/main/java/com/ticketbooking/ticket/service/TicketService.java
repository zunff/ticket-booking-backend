package com.ticketbooking.ticket.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.ticket.entity.Ticket;

import java.util.List;

public interface TicketService extends IService<Ticket> {
    
    Ticket createTicket(Ticket ticket);
    
    Ticket updateTicket(Ticket ticket);
    
    List<Ticket> getAllTickets();
    
    List<Ticket> getAvailableTickets();
    
    Ticket getTicketById(Long id);
    
    void startSale(Long ticketId);
    
    void stopSale(Long ticketId);
    
    void deleteTicket(Long ticketId);
    
    void syncStockToRedis(Long ticketId);
}

package com.ticketbooking.ticket.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.ticket.entity.Order;
import com.ticketbooking.ticket.entity.Ticket;

import java.util.List;

public interface TicketService extends IService<Ticket> {
    Ticket createTicket(Ticket ticket);
    List<Ticket> getAllTickets();
    List<Ticket> getAvailableTickets();
    Ticket getTicketById(Long id);
    String bookTicket(Long ticketId, Long userId, Integer quantity);
    Order getOrderByOrderNo(String orderNo);
    List<Order> getOrdersByUserId(Long userId);
}

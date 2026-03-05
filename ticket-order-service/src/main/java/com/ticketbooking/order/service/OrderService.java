package com.ticketbooking.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.order.model.dto.TicketInfoDTO;
import com.ticketbooking.order.entity.Order;

import java.math.BigDecimal;
import java.util.List;

public interface OrderService extends IService<Order> {
    
    String createOrder(Long userId, Long ticketId, Integer quantity);
    
    Order findByOrderNo(String orderNo);
    
    List<Order> findByUserId(Long userId);
    
    TicketInfoDTO getTicketInfo(Long ticketId);
    
    boolean checkUserBoughtTicket(Long ticketId, Long userId);
    
    Order createOrderFromStock(String orderNo, Long userId, Long ticketId, 
                               Integer quantity, BigDecimal totalPrice, String status);
}

package com.ticketbooking.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.order.model.dto.TicketInfoDTO;
import com.ticketbooking.order.entity.Order;

import java.util.List;

public interface OrderService extends IService<Order> {
    
    String createOrder(Long userId, Long concertId, Long gradeId, Integer quantity);
    
    Order findByOrderNo(String orderNo);
    
    List<Order> findByUserId(Long userId);

    
    Order createOrderFromStock(String orderNo, Long userId, Long concertId, Long gradeId,
                               Integer quantity, Integer totalPrice, Integer status);
}

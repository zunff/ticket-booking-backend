package com.ticketbooking.ticket.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.ticket.entity.Order;

import java.util.List;

public interface OrderService extends IService<Order> {

    Order findByOrderNo(String orderNo);

    List<Order> findByUserId(Long userId);

    void createOrder(String orderNo, Long userId, Long ticketId, Integer quantity, java.math.BigDecimal totalPrice, String status);
}

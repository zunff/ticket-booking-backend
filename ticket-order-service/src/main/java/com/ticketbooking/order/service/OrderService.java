package com.ticketbooking.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.model.dto.OrderDTO;
import com.ticketbooking.common.model.qo.CreateOrderQO;
import com.ticketbooking.order.model.dto.TicketInfoDTO;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.model.vo.OrderVO;

import java.util.List;

public interface OrderService extends IService<Order> {

    String createOrder(Long userId, Long concertId, Long gradeId, Integer quantity);

    Order findByOrderNo(String orderNo);

    List<Order> findByUserId(Long userId);

    Order createOrderFromStock(String orderNo, Long userId, Long concertId, Long gradeId,
                               Integer quantity, Integer totalPrice, Integer status);

    OrderDTO findDTOByOrderNo(String orderNo);

    OrderDTO createOrderDTO(CreateOrderQO qo);

    /**
     * 分页查询订单（管理员）
     */
    PageResult<OrderVO> getOrderPage(Long current, Long size, Long userId, Integer status, String orderNo);

    /**
     * 根据订单号获取订单VO（管理员）
     */
    OrderVO getOrderVOByOrderNo(String orderNo);

    /**
     * 根据用户ID获取订单VO列表（管理员）
     */
    List<OrderVO> getOrderVOsByUserId(Long userId);

    /**
     * 分页查询用户订单（带关联信息）
     */
    PageResult<OrderVO> getOrderPageByUserId(Long userId, Long current, Long size, Integer status);
}

package com.ticketbooking.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.model.dto.DashboardStatsDTO;
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
     * 标记订单失败
     * @param orderNo 订单号
     * @param failReason 失败原因
     */
    void markOrderFailed(String orderNo, String failReason);

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

    /**
     * 获取仪表盘订单统计数据
     */
    DashboardStatsDTO getDashboardStats();

    /**
     * 检查用户是否已购买过指定档位的票
     * @param userId 用户ID
     * @param concertId 演唱会ID
     * @param gradeId 档位ID
     * @return true-已购买，false-未购买
     */
    boolean hasUserBought(Long userId, Long concertId, Long gradeId);

    /**
     * 查询用户在演唱会的已购买数量（限购校验用）
     * @param userId 用户ID
     * @param concertId 演唱会ID
     * @return 已购买数量（只统计已支付订单）
     */
    int countUserPurchased(Long userId, Long concertId);
}

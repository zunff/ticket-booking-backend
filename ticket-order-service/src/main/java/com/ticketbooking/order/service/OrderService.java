package com.ticketbooking.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.model.dto.ConcertSalesDTO;
import com.ticketbooking.common.model.dto.DashboardStatsDTO;
import com.ticketbooking.common.model.dto.OrderDTO;
import com.ticketbooking.common.model.dto.PayResponseDTO;
import com.ticketbooking.common.model.dto.SalesDataDTO;
import com.ticketbooking.common.model.qo.CreateOrderQO;
import com.ticketbooking.order.model.dto.TicketInfoDTO;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.model.qo.InitiatePayQO;
import com.ticketbooking.order.model.vo.OrderVO;

import java.util.List;

public interface OrderService extends IService<Order> {

    String createOrder(Long userId, Long concertId, Long gradeId, Integer quantity);

    Order findByOrderNo(String orderNo);

    List<Order> findByUserId(Long userId);

    OrderDTO findDTOByOrderNo(String orderNo);

    OrderDTO createOrderDTO(CreateOrderQO qo);

    /**
     * 标记订单失败
     * @param orderNo 订单号
     * @param failReason 失败原因
     */
    void markOrderFailed(String orderNo, String failReason);

    /**
     * 标记订单已支付（库存已扣减，等待用户付款）
     * @param orderNo 订单号
     */
    void markOrderPending(String orderNo);

    /**
     * 标记订单已支付（支付成功回调）
     * @param orderNo 订单号
     */
    void markOrderPaid(String orderNo);

    /**
     * 标记订单已取消（超时/退款）
     */
    void markOrderCancelled(String orderNo, String reason);

    /**
     * 仅回补 Redis 预扣减库存（订单仍为 PROCESSING：Kafka 未成功消费、DB 未扣减时使用）
     */
    void rollbackRedisStockOnly(Order order);

    /**
     * 发起支付：校验订单可支付后调 payment 模块 prepay
     */
    PayResponseDTO initiatePayment(Long userId, String orderNo, InitiatePayQO qo);

    /**
     * 取消订单并退款（管理员）
     */
    void cancelAndRefund(String orderNo);

    /**
     * 查询超时未支付的 PENDING 订单（超时关单 Job 使用）
     */
    List<Order> findStalePendingOrders(java.time.LocalDateTime before, int limit);

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

    /**
     * 获取最近N天的销售数据
     * @param days 天数
     * @return 每天的销售数据
     */
    List<SalesDataDTO> getSalesData(Integer days);

    /**
     * 获取各演唱会的销售统计
     * @return 演唱会销售统计列表
     */
    List<ConcertSalesDTO> getConcertSalesStats();
}

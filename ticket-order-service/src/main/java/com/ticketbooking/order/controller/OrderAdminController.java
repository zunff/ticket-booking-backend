package com.ticketbooking.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticketbooking.common.annotation.RequireAdmin;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.model.dto.TicketGradeDTO;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.order.client.TicketServiceClient;
import com.ticketbooking.order.converter.OrderConverter;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.service.OrderService;
import com.ticketbooking.order.model.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class OrderAdminController {

    private final OrderService orderService;
    private final OrderConverter orderConverter;
    private final TicketServiceClient ticketServiceClient;

    @GetMapping
    @RequireAdmin
    public Result<PageResult<OrderVO>> getAllOrders(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String orderNo) {

        // Create page request
        Page<Order> page = new Page<>(current, size);

        // Query with conditions
        IPage<Order> orderPage = orderService.page(page,
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                .eq(userId != null, Order::getUserId, userId)
                .eq(status != null, Order::getStatus, status)
                .eq(orderNo != null, Order::getOrderNo, orderNo)
                .orderByDesc(Order::getCreateTime)
        );

        // Convert to VO
        List<OrderVO> orderVOs = orderConverter.toVOList(orderPage.getRecords());

        // Fetch concert and grade names
        List<Long> gradeIds = orderVOs.stream()
            .map(OrderVO::getGradeId)
            .distinct()
            .collect(Collectors.toList());

        Map<Long, TicketGradeDTO> gradeInfoMap = gradeIds.stream()
            .collect(Collectors.toMap(
                id -> id,
                id -> {
                    try {
                        return ticketServiceClient.getGradeById(id);
                    } catch (Exception e) {
                        return new TicketGradeDTO();
                    }
                }
            ));

        // Fill in concert and grade names
        orderVOs.forEach(vo -> {
            TicketGradeDTO gradeInfo = gradeInfoMap.get(vo.getGradeId());
            if (gradeInfo != null) {
                vo.setConcertName(gradeInfo.getConcertName());
                vo.setGradeName(gradeInfo.getGradeName());
            }
        });

        // Convert to PageResult
        PageResult<OrderVO> pageResult = PageResult.of(
            orderVOs,
            orderPage.getTotal(),
            orderPage.getCurrent(),
            orderPage.getSize()
        );

        return Result.success(pageResult);
    }

    @GetMapping("/{orderNo}")
    @RequireAdmin
    public Result<OrderVO> getOrderByOrderNo(@PathVariable String orderNo) {
        Order order = orderService.findByOrderNo(orderNo);
        OrderVO vo = orderConverter.toVO(order);

        // Fetch concert and grade names
        try {
            TicketGradeDTO gradeInfo = ticketServiceClient.getGradeById(order.getGradeId());
            if (gradeInfo != null) {
                vo.setConcertName(gradeInfo.getConcertName());
                vo.setGradeName(gradeInfo.getGradeName());
            }
        } catch (Exception e) {
            // Ignore if service is unavailable
        }

        return Result.success(vo);
    }

    @GetMapping("/user/{userId}")
    @RequireAdmin
    public Result<List<OrderVO>> getOrdersByUserId(@PathVariable Long userId) {
        List<Order> orders = orderService.findByUserId(userId);
        List<OrderVO> orderVOs = orderConverter.toVOList(orders);

        // Fetch concert and grade names
        List<Long> gradeIds = orderVOs.stream()
            .map(OrderVO::getGradeId)
            .distinct()
            .collect(Collectors.toList());

        Map<Long, TicketGradeDTO> gradeInfoMap = gradeIds.stream()
            .collect(Collectors.toMap(
                id -> id,
                id -> {
                    try {
                        return ticketServiceClient.getGradeById(id);
                    } catch (Exception e) {
                        return new TicketGradeDTO();
                    }
                }
            ));

        // Fill in concert and grade names
        orderVOs.forEach(vo -> {
            TicketGradeDTO gradeInfo = gradeInfoMap.get(vo.getGradeId());
            if (gradeInfo != null) {
                vo.setConcertName(gradeInfo.getConcertName());
                vo.setGradeName(gradeInfo.getGradeName());
            }
        });

        return Result.success(orderVOs);
    }
}

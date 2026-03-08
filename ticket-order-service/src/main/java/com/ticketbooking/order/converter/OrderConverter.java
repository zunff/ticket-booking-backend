package com.ticketbooking.order.converter;

import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.model.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单实体与VO转换器
 */
@Component
public class OrderConverter {

    public OrderVO toVO(Order order) {
        if (order == null) return null;
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        return vo;
    }

    public List<OrderVO> toVOList(List<Order> orders) {
        return orders.stream().map(this::toVO).collect(Collectors.toList());
    }
}

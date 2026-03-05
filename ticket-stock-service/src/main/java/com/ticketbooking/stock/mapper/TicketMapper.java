package com.ticketbooking.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticketbooking.stock.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {

    int decrementStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    int incrementStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}

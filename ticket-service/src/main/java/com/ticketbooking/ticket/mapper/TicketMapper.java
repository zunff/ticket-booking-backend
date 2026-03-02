package com.ticketbooking.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticketbooking.ticket.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {

    List<Ticket> findAvailableTickets();

    Ticket findByIdForUpdate(@Param("id") Long id);

    int decrementStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    int incrementStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}

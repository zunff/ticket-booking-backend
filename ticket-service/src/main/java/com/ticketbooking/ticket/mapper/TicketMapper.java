package com.ticketbooking.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticketbooking.ticket.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
    
    @Select("SELECT * FROM tickets WHERE available_stock > 0 AND start_time <= NOW() AND end_time >= NOW()")
    List<Ticket> findAvailableTickets();
    
    @Select("SELECT * FROM tickets WHERE id = #{id} FOR UPDATE")
    Ticket findByIdForUpdate(@Param("id") Long id);
    
    @Update("UPDATE tickets SET available_stock = available_stock - #{quantity}, update_time = NOW() WHERE id = #{id} AND available_stock >= #{quantity}")
    int decrementStock(@Param("id") Long id, @Param("quantity") Integer quantity);
    
    @Update("UPDATE tickets SET available_stock = available_stock + #{quantity}, update_time = NOW() WHERE id = #{id}")
    int incrementStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}

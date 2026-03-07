package com.ticketbooking.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticketbooking.ticket.entity.TicketGrade;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TicketGradeMapper extends BaseMapper<TicketGrade> {
    
    List<TicketGrade> findByConcertId(Long concertId);
}

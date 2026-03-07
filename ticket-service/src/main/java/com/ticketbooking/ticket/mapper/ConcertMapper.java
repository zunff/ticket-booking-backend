package com.ticketbooking.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticketbooking.ticket.entity.Concert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConcertMapper extends BaseMapper<Concert> {
}

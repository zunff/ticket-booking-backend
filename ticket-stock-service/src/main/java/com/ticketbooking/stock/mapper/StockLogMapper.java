package com.ticketbooking.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticketbooking.stock.entity.StockLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockLogMapper extends BaseMapper<StockLog> {
}

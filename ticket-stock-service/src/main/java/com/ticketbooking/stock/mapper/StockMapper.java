package com.ticketbooking.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticketbooking.stock.entity.Stock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StockMapper extends BaseMapper<Stock> {

    /**
     * 乐观锁扣减库存（原子操作，需保留 XML）
     */
    int decrementStock(@Param("concertId") Long concertId,
                       @Param("gradeId") Long gradeId,
                       @Param("quantity") Integer quantity,
                       @Param("version") Integer version);
}

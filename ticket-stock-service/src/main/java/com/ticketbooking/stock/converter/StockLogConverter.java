package com.ticketbooking.stock.converter;

import com.ticketbooking.stock.entity.StockLog;
import com.ticketbooking.stock.model.vo.StockLogVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 库存日志转换器
 */
@Component
public class StockLogConverter {

    public StockLogVO toVO(StockLog entity) {
        if (entity == null) return null;
        StockLogVO vo = new StockLogVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    public List<StockLogVO> toVOList(List<StockLog> entities) {
        return entities.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }
}

package com.ticketbooking.stock.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.stock.entity.Stock;
import com.ticketbooking.stock.entity.StockLog;
import com.ticketbooking.stock.model.qo.StockLogQueryQO;

import java.util.List;
import java.util.Map;

public interface StockService extends IService<Stock> {

    int decrementStock(Long concertId, Long gradeId, Integer quantity, String orderNo);

    /**
     * 恢复库存（订单取消/退款时调用）：DB 乐观锁加回 + Redis Hash 库存回补。
     * 用户购买计数器的回滚由调用方（order 超时/退款流程）负责，本方法只管库存。
     */
    int restoreStock(Long concertId, Long gradeId, Integer quantity, String orderNo);

    Stock getStockByConcertAndGrade(Long concertId, Long gradeId);

    List<StockLog> getStockLogs(Long concertId, Long gradeId);

    void adjustStock(Long concertId, Long gradeId, Integer newStock, String remark);

    /**
     * 分页查询库存日志
     */
    IPage<StockLog> getStockLogsPage(StockLogQueryQO qo);

    /**
     * 获取库存DTO（包含演出和档位信息）
     */
    StockDTO getStockDTO(Long concertId, Long gradeId);

    /**
     * 获取指定演出的所有库存DTO列表
     */
    List<StockDTO> getStockDTOsByConcertId(Long concertId);

    /**
     * 获取指定演出的库存映射 (gradeId -> availableStock)
     */
    Map<Long, Integer> getStockMapByConcertId(Long concertId);

    /**
     * 初始化库存记录
     */
    void initStock(Long concertId, Long gradeId, Integer totalStock);

    /**
     * 批量删除库存记录
     */
    void deleteByGradeIds(List<Long> gradeIds);

    /**
     * 更新库存数量
     */
    void updateStock(Long concertId, Long gradeId, Integer newStock);
}

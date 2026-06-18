package com.ticketbooking.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.constant.RedisExpireConstants;
import com.ticketbooking.common.constant.RedisKeyConstants;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.common.model.dto.TicketGradeDTO;
import com.ticketbooking.common.utils.RedisUtils;
import com.ticketbooking.stock.client.TicketServiceClient;
import com.ticketbooking.stock.entity.Stock;
import com.ticketbooking.stock.entity.StockLog;
import com.ticketbooking.stock.mapper.StockLogMapper;
import com.ticketbooking.stock.mapper.StockMapper;
import com.ticketbooking.stock.model.qo.StockLogQueryQO;
import com.ticketbooking.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl extends ServiceImpl<StockMapper, Stock> implements StockService {

    private final StockLogMapper stockLogMapper;
    private final RedisUtils redisUtils;
    private final TicketServiceClient ticketServiceClient;
    
    @Override
    @Transactional
    public int decrementStock(Long concertId, Long gradeId, Integer quantity, String orderNo) {
        Stock stock = getStockByConcertAndGrade(concertId, gradeId);
        if (stock == null) {
            log.warn("Stock not found: concertId={}, gradeId={}", concertId, gradeId);
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        int beforeStock = stock.getAvailableStock();
        if (beforeStock < quantity) {
            log.warn("Insufficient stock: concertId={}, gradeId={}, available={}, required={}",
                    concertId, gradeId, beforeStock, quantity);
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }

        int updated = baseMapper.decrementStock(concertId, gradeId, quantity, stock.getVersion());
        if (updated > 0) {
            int afterStock = beforeStock - quantity;
            recordStockLog(concertId, gradeId, orderNo, -quantity, beforeStock, afterStock, "DECREMENT", "订单扣减库存");
            log.info("Stock decremented: concertId={}, gradeId={}, quantity={}, before={}, after={}", concertId, gradeId, quantity, beforeStock, afterStock);
        }

        return updated;
    }

    @Override
    @Transactional
    public int restoreStock(Long concertId, Long gradeId, Integer quantity, String orderNo) {
        Stock stock = getStockByConcertAndGrade(concertId, gradeId);
        if (stock == null) {
            log.warn("Stock not found, skip restore: concertId={}, gradeId={}", concertId, gradeId);
            return 0;
        }

        int beforeStock = stock.getAvailableStock();
        int updated = baseMapper.incrementStock(concertId, gradeId, quantity, stock.getVersion());
        if (updated > 0) {
            int afterStock = beforeStock + quantity;
            recordStockLog(concertId, gradeId, orderNo, quantity, beforeStock, afterStock, "RESTORE", "订单取消/退款恢复库存");

            // 回补 Redis 库存 Hash（仅当 field 存在时，避免误创建）
            String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(concertId);
            if (Boolean.TRUE.equals(redisUtils.hExists(stockHashKey, String.valueOf(gradeId)))) {
                redisUtils.hIncrBy(stockHashKey, String.valueOf(gradeId), quantity);
            }

            log.info("Stock restored: concertId={}, gradeId={}, quantity={}, before={}, after={}",
                    concertId, gradeId, quantity, beforeStock, afterStock);
        } else {
            log.warn("Stock restore version conflict: concertId={}, gradeId={}, orderNo={}", concertId, gradeId, orderNo);
        }
        return updated;
    }

    @Override
    public Stock getStockByConcertAndGrade(Long concertId, Long gradeId) {
        return getOne(new LambdaQueryWrapper<Stock>()
                .eq(Stock::getConcertId, concertId)
                .eq(Stock::getGradeId, gradeId));
    }
    
    @Override
    public List<StockLog> getStockLogs(Long concertId, Long gradeId) {
        return stockLogMapper.selectList(
                new LambdaQueryWrapper<StockLog>()
                        .eq(StockLog::getConcertId, concertId)
                        .eq(StockLog::getGradeId, gradeId)
                        .orderByDesc(StockLog::getCreateTime));
    }
    
    @Override
    @Transactional
    public void adjustStock(Long concertId, Long gradeId, Integer newStock, String remark) {
        Stock stock = getStockByConcertAndGrade(concertId, gradeId);
        if (stock == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        int beforeStock = stock.getAvailableStock();
        int changeAmount = newStock - beforeStock;

        stock.setAvailableStock(newStock);
        updateById(stock);

        recordStockLog(concertId, gradeId, "ADMIN", changeAmount, beforeStock, newStock, "ADJUST", remark);

        // 使用 Hash 结构更新 Redis
        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(concertId);
        if (Boolean.TRUE.equals(redisUtils.hExists(stockHashKey, String.valueOf(gradeId)))) {
            redisUtils.hSet(stockHashKey, String.valueOf(gradeId), String.valueOf(newStock));
        }

        log.info("Stock adjusted: concertId={}, gradeId={}, before={}, after={}, remark={}",
                concertId, gradeId, beforeStock, newStock, remark);
    }
    
    private void recordStockLog(Long concertId, Long gradeId, String orderNo, Integer changeAmount,
                                Integer beforeStock, Integer afterStock,
                                String operationType, String remark) {
        StockLog stockLog = new StockLog();
        stockLog.setConcertId(concertId);
        stockLog.setGradeId(gradeId);
        stockLog.setOrderNo(orderNo);
        stockLog.setChangeAmount(changeAmount);
        stockLog.setBeforeStock(beforeStock);
        stockLog.setAfterStock(afterStock);
        stockLog.setOperationType(operationType);
        stockLog.setRemark(remark);
        stockLog.setCreateTime(LocalDateTime.now());
        stockLogMapper.insert(stockLog);
    }

    @Override
    public IPage<StockLog> getStockLogsPage(StockLogQueryQO qo) {
        LambdaQueryWrapper<StockLog> wrapper = new LambdaQueryWrapper<>();

        if (qo.getConcertId() != null) {
            wrapper.eq(StockLog::getConcertId, qo.getConcertId());
        }
        if (qo.getGradeId() != null) {
            wrapper.eq(StockLog::getGradeId, qo.getGradeId());
        }

        wrapper.orderByDesc(StockLog::getCreateTime);

        Page<StockLog> page = new Page<>(qo.getCurrent(), qo.getSize());
        return stockLogMapper.selectPage(page, wrapper);
    }

    @Override
    public StockDTO getStockDTO(Long concertId, Long gradeId) {
        Stock stock = getStockByConcertAndGrade(concertId, gradeId);
        if (stock == null) {
            return null;
        }

        TicketGradeDTO grade = ticketServiceClient.getGradeById(gradeId);
        if (grade == null) {
            return null;
        }

        StockDTO dto = new StockDTO();
        dto.setId(stock.getId());
        dto.setConcertId(concertId);
        dto.setConcertName(grade.getConcertName());
        dto.setGradeId(gradeId);
        dto.setGradeName(grade.getGradeName());
        dto.setPrice(grade.getPrice());
        dto.setAvailableStock(stock.getAvailableStock());
        dto.setPurchaseLimit(grade.getPurchaseLimit());

        return dto;
    }

    @Override
    public List<StockDTO> getStockDTOsByConcertId(Long concertId) {
        List<Stock> stocks = list(
                new LambdaQueryWrapper<Stock>()
                        .eq(Stock::getConcertId, concertId)
        );

        return stocks.stream()
                .map(stock -> {
                    StockDTO dto = new StockDTO();
                    dto.setId(stock.getId());
                    dto.setConcertId(stock.getConcertId());
                    dto.setGradeId(stock.getGradeId());
                    dto.setAvailableStock(stock.getAvailableStock());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<Long, Integer> getStockMapByConcertId(Long concertId) {
        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(concertId);
        Map<Object, Object> cache = redisUtils.hGetAll(stockHashKey);

        // 缓存命中
        if (cache != null && !cache.isEmpty()) {
            //判断是否为空值缓存标记
            if (cache.containsKey(RedisKeyConstants.EMPTY_KEY)) {
                log.debug("Hit empty stock cache for concertId={}", concertId);
                return Map.of();
            }
            // 正常数据转换
            return cache.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> Long.parseLong(e.getKey().toString()),
                            e -> Integer.parseInt(e.getValue().toString())
                    ));
        }

        // 缓存未命中，查数据库
        List<Stock> stocks = list(
                new LambdaQueryWrapper<Stock>()
                        .eq(Stock::getConcertId, concertId)
        );

        // 查询结果为空时，缓存空值防止穿透
        if (stocks.isEmpty()) {
            redisUtils.hSet(stockHashKey, RedisKeyConstants.EMPTY_KEY, "1");
            redisUtils.expire(stockHashKey, RedisExpireConstants.NULL_CACHE_SECONDS, TimeUnit.SECONDS);
            log.debug("Cached empty stock result for concertId={}", concertId);
            return Map.of();
        }

        // 有数据，回写到缓存
        Map<Long, Integer> result = stocks.stream()
                .collect(Collectors.toMap(
                        Stock::getGradeId,
                        Stock::getAvailableStock,
                        (existing, replacement) -> existing
                ));

        Map<String, String> stockMap = result.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> String.valueOf(e.getKey()),
                        e -> String.valueOf(e.getValue())
                ));

        redisUtils.hMSet(stockHashKey, stockMap);
        redisUtils.expire(stockHashKey, RedisExpireConstants.PREHEAT_CACHE_HOURS, TimeUnit.HOURS);

        log.debug("Cached stock data for concertId={}, grades={}", concertId, result.size());
        return result;
    }

    @Override
    public void initStock(Long concertId, Long gradeId, Integer totalStock) {
        Stock existingStock = getStockByConcertAndGrade(concertId, gradeId);
        if (existingStock == null) {
            Stock newStock = new Stock();
            newStock.setConcertId(concertId);
            newStock.setGradeId(gradeId);
            newStock.setAvailableStock(totalStock);
            newStock.setVersion(0);
            save(newStock);

            log.info("Stock initialized in DB: concertId={}, gradeId={}, stock={}", concertId, gradeId, totalStock);
        }
    }

    @Override
    @Transactional
    public void deleteByGradeIds(List<Long> gradeIds) {
        if (gradeIds == null || gradeIds.isEmpty()) {
            return;
        }

        // 批量删除库存记录
        remove(new LambdaQueryWrapper<Stock>().in(Stock::getGradeId, gradeIds));

        // 批量删除库存日志
        stockLogMapper.delete(new LambdaQueryWrapper<StockLog>().in(StockLog::getGradeId, gradeIds));

        log.info("Stock deleted by gradeIds: {}", gradeIds);
    }

    @Override
    @Transactional
    public void updateStock(Long concertId, Long gradeId, Integer newStock) {
        Stock stock = getStockByConcertAndGrade(concertId, gradeId);
        if (stock == null) {
            // 不存在则创建
            initStock(concertId, gradeId, newStock);
            return;
        }

        int beforeStock = stock.getAvailableStock();
        stock.setAvailableStock(newStock);
        updateById(stock);

        // 记录日志
        recordStockLog(concertId, gradeId, "UPDATE", newStock - beforeStock, beforeStock, newStock, "UPDATE", "票档库存更新");

        // 更新 Redis 缓存
        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(concertId);
        if (Boolean.TRUE.equals(redisUtils.hExists(stockHashKey, String.valueOf(gradeId)))) {
            redisUtils.hSet(stockHashKey, String.valueOf(gradeId), String.valueOf(newStock));
        }

        log.info("Stock updated: concertId={}, gradeId={}, before={}, after={}", concertId, gradeId, beforeStock, newStock);
    }
}

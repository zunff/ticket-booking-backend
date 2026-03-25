package com.ticketbooking.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl extends ServiceImpl<StockMapper, Stock> implements StockService {

    private final StockLogMapper stockLogMapper;
    private final RedisUtils redisUtils;
    private final TicketServiceClient ticketServiceClient;
    private static final long CACHE_EXPIRE_SECONDS = 3600;
    
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

            // 只有 key 存在时才更新 Redis
            String stockKey = RedisKeyConstants.buildTicketStockKey(concertId, gradeId);
            if (redisUtils.hasKey(stockKey)) {
                redisUtils.set(stockKey, String.valueOf(afterStock));
            }

            log.info("Stock decremented: concertId={}, gradeId={}, quantity={}, before={}, after={}",
                    concertId, gradeId, quantity, beforeStock, afterStock);
        }

        return updated;
    }
    
    @Override
    @Transactional
    public int incrementStock(Long concertId, Long gradeId, Integer quantity, String orderNo) {
        Stock stock = getStockByConcertAndGrade(concertId, gradeId);
        if (stock == null) {
            log.warn("Stock not found: concertId={}, gradeId={}", concertId, gradeId);
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        int beforeStock = stock.getAvailableStock();

        int updated = baseMapper.incrementStock(concertId, gradeId, quantity);
        if (updated == 0) {
            log.warn("Stock increment failed: concertId={}, gradeId={}", concertId, gradeId);
            throw new BusinessException(ErrorCode.STOCK_ROLLBACK_FAILED);
        }

        int afterStock = beforeStock + quantity;
        recordStockLog(concertId, gradeId, orderNo, quantity, beforeStock, afterStock, "INCREMENT", "订单回滚库存");

        // 只有 key 存在时才更新 Redis
        String stockKey = RedisKeyConstants.buildTicketStockKey(concertId, gradeId);
        if (redisUtils.hasKey(stockKey)) {
            redisUtils.set(stockKey, String.valueOf(afterStock));
        }

        log.info("Stock incremented: concertId={}, gradeId={}, quantity={}, before={}, after={}",
                concertId, gradeId, quantity, beforeStock, afterStock);

        return updated;
    }
    
    @Override
    public Stock getStockByConcertAndGrade(Long concertId, Long gradeId) {
        return baseMapper.findByConcertAndGrade(concertId, gradeId);
    }
    
    @Override
    public Integer getAvailableStock(Long concertId, Long gradeId) {
        String stockKey = RedisKeyConstants.buildTicketStockKey(concertId, gradeId);
        String cachedStock = redisUtils.get(stockKey);

        if (cachedStock != null) {
            return Integer.parseInt(cachedStock);
        }

        Stock stock = getStockByConcertAndGrade(concertId, gradeId);
        if (stock == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        redisUtils.setEx(stockKey, String.valueOf(stock.getAvailableStock()), CACHE_EXPIRE_SECONDS);
        return stock.getAvailableStock();
    }
    
    @Override
    public void syncStockToRedis(Long concertId, Long gradeId) {
        Stock stock = getStockByConcertAndGrade(concertId, gradeId);
        if (stock != null) {
            String stockKey = RedisKeyConstants.buildTicketStockKey(concertId, gradeId);
            redisUtils.set(stockKey, String.valueOf(stock.getAvailableStock()));
            log.info("Stock synced to Redis: concertId={}, gradeId={}, stock={}", 
                    concertId, gradeId, stock.getAvailableStock());
        }
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

        // 只有 key 存在时才更新 Redis
        String stockKey = RedisKeyConstants.buildTicketStockKey(concertId, gradeId);
        if (redisUtils.hasKey(stockKey)) {
            redisUtils.set(stockKey, String.valueOf(newStock));
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
}

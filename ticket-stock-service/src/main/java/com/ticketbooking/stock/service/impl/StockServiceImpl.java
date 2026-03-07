package com.ticketbooking.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.constant.RedisKeyConstants;
import com.ticketbooking.common.utils.RedisUtils;
import com.ticketbooking.stock.entity.Stock;
import com.ticketbooking.stock.entity.StockLog;
import com.ticketbooking.stock.mapper.StockLogMapper;
import com.ticketbooking.stock.mapper.StockMapper;
import com.ticketbooking.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl extends ServiceImpl<StockMapper, Stock> implements StockService {
    
    private final StockLogMapper stockLogMapper;
    private final RedisUtils redisUtils;
    private static final long CACHE_EXPIRE_SECONDS = 3600;
    
    @Override
    @Transactional
    public int decrementStock(Long concertId, Long gradeId, Integer quantity, String orderNo) {
        Stock stock = getStockByConcertAndGrade(concertId, gradeId);
        if (stock == null) {
            log.warn("Stock not found: concertId={}, gradeId={}", concertId, gradeId);
            return 0;
        }
        
        int beforeStock = stock.getAvailableStock();
        if (beforeStock < quantity) {
            log.warn("Insufficient stock: concertId={}, gradeId={}, available={}, required={}", 
                    concertId, gradeId, beforeStock, quantity);
            return 0;
        }
        
        int updated = baseMapper.decrementStock(concertId, gradeId, quantity, stock.getVersion());
        if (updated > 0) {
            int afterStock = beforeStock - quantity;
            recordStockLog(concertId, gradeId, orderNo, -quantity, beforeStock, afterStock, "DECREMENT", "订单扣减库存");
            
            String stockKey = RedisKeyConstants.buildTicketStockKey(concertId, gradeId);
            redisUtils.set(stockKey, String.valueOf(afterStock));
            
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
            return 0;
        }
        
        int beforeStock = stock.getAvailableStock();
        
        int updated = baseMapper.incrementStock(concertId, gradeId, quantity);
        if (updated > 0) {
            int afterStock = beforeStock + quantity;
            recordStockLog(concertId, gradeId, orderNo, quantity, beforeStock, afterStock, "INCREMENT", "订单回滚库存");
            
            String stockKey = RedisKeyConstants.buildTicketStockKey(concertId, gradeId);
            redisUtils.set(stockKey, String.valueOf(afterStock));
            
            log.info("Stock incremented: concertId={}, gradeId={}, quantity={}, before={}, after={}", 
                    concertId, gradeId, quantity, beforeStock, afterStock);
        }
        
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
        if (stock != null) {
            redisUtils.setEx(stockKey, String.valueOf(stock.getAvailableStock()), CACHE_EXPIRE_SECONDS);
            return stock.getAvailableStock();
        }
        
        return null;
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
            throw new RuntimeException("库存不存在");
        }
        
        int beforeStock = stock.getAvailableStock();
        int changeAmount = newStock - beforeStock;
        
        stock.setAvailableStock(newStock);
        updateById(stock);
        
        recordStockLog(concertId, gradeId, "ADMIN", changeAmount, beforeStock, newStock, "ADJUST", remark);
        
        String stockKey = RedisKeyConstants.buildTicketStockKey(concertId, gradeId);
        redisUtils.set(stockKey, String.valueOf(newStock));
        
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
}

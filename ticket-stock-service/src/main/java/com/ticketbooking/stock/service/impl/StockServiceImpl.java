package com.ticketbooking.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.utils.RedisUtils;
import com.ticketbooking.stock.constant.RedisKeyConstants;
import com.ticketbooking.stock.entity.StockLog;
import com.ticketbooking.stock.entity.Ticket;
import com.ticketbooking.stock.mapper.StockLogMapper;
import com.ticketbooking.stock.mapper.TicketMapper;
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
public class StockServiceImpl extends ServiceImpl<TicketMapper, Ticket> implements StockService {
    
    private final StockLogMapper stockLogMapper;
    private final RedisUtils redisUtils;
    private static final long CACHE_EXPIRE_SECONDS = 3600;
    
    @Override
    @Transactional
    public int decrementStock(Long ticketId, Integer quantity, String orderNo) {
        Ticket ticket = getById(ticketId);
        if (ticket == null) {
            log.warn("Ticket not found: {}", ticketId);
            return 0;
        }
        
        int beforeStock = ticket.getAvailableStock();
        if (beforeStock < quantity) {
            log.warn("Insufficient stock: ticketId={}, available={}, required={}", 
                    ticketId, beforeStock, quantity);
            return 0;
        }
        
        int updated = baseMapper.decrementStock(ticketId, quantity);
        if (updated > 0) {
            int afterStock = beforeStock - quantity;
            recordStockLog(ticketId, orderNo, -quantity, beforeStock, afterStock, "DECREMENT", "订单扣减库存");
            
            String stockKey = RedisKeyConstants.buildTicketStockKey(ticketId);
            redisUtils.set(stockKey, String.valueOf(afterStock));
            
            log.info("Stock decremented: ticketId={}, quantity={}, before={}, after={}", 
                    ticketId, quantity, beforeStock, afterStock);
        }
        
        return updated;
    }
    
    @Override
    @Transactional
    public int incrementStock(Long ticketId, Integer quantity, String orderNo) {
        Ticket ticket = getById(ticketId);
        if (ticket == null) {
            log.warn("Ticket not found: {}", ticketId);
            return 0;
        }
        
        int beforeStock = ticket.getAvailableStock();
        
        int updated = baseMapper.incrementStock(ticketId, quantity);
        if (updated > 0) {
            int afterStock = beforeStock + quantity;
            recordStockLog(ticketId, orderNo, quantity, beforeStock, afterStock, "INCREMENT", "订单回滚库存");
            
            String stockKey = RedisKeyConstants.buildTicketStockKey(ticketId);
            redisUtils.set(stockKey, String.valueOf(afterStock));
            
            log.info("Stock incremented: ticketId={}, quantity={}, before={}, after={}", 
                    ticketId, quantity, beforeStock, afterStock);
        }
        
        return updated;
    }
    
    @Override
    public Ticket getTicketById(Long ticketId) {
        return getById(ticketId);
    }
    
    @Override
    public Integer getAvailableStock(Long ticketId) {
        String stockKey = RedisKeyConstants.buildTicketStockKey(ticketId);
        String cachedStock = redisUtils.get(stockKey);
        
        if (cachedStock != null) {
            return Integer.parseInt(cachedStock);
        }
        
        Ticket ticket = getById(ticketId);
        if (ticket != null) {
            redisUtils.setEx(stockKey, String.valueOf(ticket.getAvailableStock()), CACHE_EXPIRE_SECONDS);
            return ticket.getAvailableStock();
        }
        
        return null;
    }
    
    @Override
    public void syncStockToRedis(Long ticketId) {
        Ticket ticket = getById(ticketId);
        if (ticket != null) {
            String stockKey = RedisKeyConstants.buildTicketStockKey(ticketId);
            redisUtils.set(stockKey, String.valueOf(ticket.getAvailableStock()));
            log.info("Stock synced to Redis: ticketId={}, stock={}", ticketId, ticket.getAvailableStock());
        }
    }
    
    @Override
    public List<StockLog> getStockLogs(Long ticketId) {
        return stockLogMapper.selectList(
                new LambdaQueryWrapper<StockLog>()
                        .eq(StockLog::getTicketId, ticketId)
                        .orderByDesc(StockLog::getCreateTime));
    }
    
    @Override
    @Transactional
    public void adjustStock(Long ticketId, Integer newStock, String remark) {
        Ticket ticket = getById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("票务不存在");
        }
        
        int beforeStock = ticket.getAvailableStock();
        int changeAmount = newStock - beforeStock;
        
        ticket.setAvailableStock(newStock);
        updateById(ticket);
        
        recordStockLog(ticketId, "ADMIN", changeAmount, beforeStock, newStock, "ADJUST", remark);
        
        String stockKey = RedisKeyConstants.buildTicketStockKey(ticketId);
        redisUtils.set(stockKey, String.valueOf(newStock));
        
        log.info("Stock adjusted: ticketId={}, before={}, after={}, remark={}", 
                ticketId, beforeStock, newStock, remark);
    }
    
    private void recordStockLog(Long ticketId, String orderNo, Integer changeAmount, 
                                Integer beforeStock, Integer afterStock, 
                                String operationType, String remark) {
        StockLog stockLog = new StockLog();
        stockLog.setTicketId(ticketId);
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

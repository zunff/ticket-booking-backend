package com.ticketbooking.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.TicketStatus;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.utils.RedisUtils;
import com.ticketbooking.ticket.constant.RedisKeyConstants;
import com.ticketbooking.ticket.entity.Ticket;
import com.ticketbooking.ticket.mapper.TicketMapper;
import com.ticketbooking.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl extends ServiceImpl<TicketMapper, Ticket> implements TicketService {
    
    private final RedisUtils redisUtils;
    private static final long CACHE_EXPIRE_SECONDS = 3600;
    
    @Override
    public Ticket createTicket(Ticket ticket) {
        ticket.setAvailableStock(ticket.getTotalStock());
        ticket.setStatus(TicketStatus.AVAILABLE.getCode());
        save(ticket);
        
        String stockKey = RedisKeyConstants.buildTicketStockKey(ticket.getId());
        redisUtils.set(stockKey, String.valueOf(ticket.getTotalStock()));
        
        cacheTicketInfo(ticket);
        
        log.info("Ticket created: id={}, name={}, stock={}", 
                ticket.getId(), ticket.getName(), ticket.getTotalStock());
        
        return ticket;
    }
    
    @Override
    public Ticket updateTicket(Ticket ticket) {
        Ticket existing = getById(ticket.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        
        updateById(ticket);
        cacheTicketInfo(ticket);
        
        if (ticket.getAvailableStock() != null) {
            String stockKey = RedisKeyConstants.buildTicketStockKey(ticket.getId());
            redisUtils.set(stockKey, String.valueOf(ticket.getAvailableStock()));
        }
        
        log.info("Ticket updated: id={}", ticket.getId());
        return ticket;
    }
    
    @Override
    public List<Ticket> getAllTickets() {
        return list();
    }
    
    @Override
    public List<Ticket> getAvailableTickets() {
        return list(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getStatus, TicketStatus.AVAILABLE.getCode())
                .gt(Ticket::getAvailableStock, 0));
    }
    
    @Override
    public Ticket getTicketById(Long id) {
        String cacheKey = "ticket:info:" + id;
        String cachedInfo = redisUtils.get(cacheKey);
        if (cachedInfo != null) {
            return parseTicketFromCache(cachedInfo);
        }
        
        Ticket ticket = getById(id);
        if (ticket != null) {
            cacheTicketInfo(ticket);
        }
        
        return ticket;
    }
    
    @Override
    public void startSale(Long ticketId) {
        Ticket ticket = getById(ticketId);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        
        ticket.setStatus(TicketStatus.AVAILABLE.getCode());
        updateById(ticket);
        
        syncStockToRedis(ticketId);
        cacheTicketInfo(ticket);
        
        log.info("Ticket sale started: id={}", ticketId);
    }
    
    @Override
    public void stopSale(Long ticketId) {
        Ticket ticket = getById(ticketId);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        
        ticket.setStatus(TicketStatus.SUSPENDED.getCode());
        updateById(ticket);
        
        cacheTicketInfo(ticket);
        
        log.info("Ticket sale stopped: id={}", ticketId);
    }
    
    @Override
    public void deleteTicket(Long ticketId) {
        removeById(ticketId);
        
        String stockKey = RedisKeyConstants.buildTicketStockKey(ticketId);
        redisUtils.delete(stockKey);
        redisUtils.delete("ticket:info:" + ticketId);
        
        log.info("Ticket deleted: id={}", ticketId);
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
    
    private void cacheTicketInfo(Ticket ticket) {
        String cacheKey = "ticket:info:" + ticket.getId();
        String cacheValue = ticket.getId() + ":" + ticket.getName() + ":" + 
                ticket.getPrice() + ":" + ticket.getAvailableStock();
        redisUtils.setEx(cacheKey, cacheValue, CACHE_EXPIRE_SECONDS);
    }
    
    private Ticket parseTicketFromCache(String cachedInfo) {
        if (cachedInfo == null || cachedInfo.isEmpty()) {
            return null;
        }
        String[] parts = cachedInfo.split(":");
        if (parts.length >= 4) {
            Ticket ticket = new Ticket();
            ticket.setId(Long.parseLong(parts[0]));
            ticket.setName(parts[1]);
            ticket.setPrice(new java.math.BigDecimal(parts[2]));
            ticket.setAvailableStock(Integer.parseInt(parts[3]));
            return ticket;
        }
        return null;
    }
    
    @Scheduled(fixedRate = 600000)
    public void preloadHotTickets() {
        log.info("Preloading hot tickets to cache...");
        
        List<Ticket> hotTickets = list(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getStatus, TicketStatus.AVAILABLE.getCode())
                .gt(Ticket::getAvailableStock, 0)
                .last("LIMIT 10"));
        
        for (Ticket ticket : hotTickets) {
            String stockKey = RedisKeyConstants.buildTicketStockKey(ticket.getId());
            String cachedStock = redisUtils.get(stockKey);
            
            if (cachedStock == null) {
                redisUtils.set(stockKey, String.valueOf(ticket.getAvailableStock()));
                log.info("Preloaded stock for ticket: id={}, stock={}", 
                        ticket.getId(), ticket.getAvailableStock());
            }
            
            cacheTicketInfo(ticket);
        }
    }
}

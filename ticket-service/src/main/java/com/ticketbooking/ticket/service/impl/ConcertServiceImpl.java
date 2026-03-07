package com.ticketbooking.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.enums.ConcertStatus;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.ticket.entity.Concert;
import com.ticketbooking.ticket.mapper.ConcertMapper;
import com.ticketbooking.ticket.service.ConcertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertServiceImpl extends ServiceImpl<ConcertMapper, Concert> implements ConcertService {
    
    @Override
    public Concert createConcert(Concert concert) {
        concert.setStatus(ConcertStatus.PENDING.getCode());
        save(concert);
        log.info("Concert created: id={}, name={}", concert.getId(), concert.getName());
        return concert;
    }
    
    @Override
    public Concert updateConcert(Concert concert) {
        Concert existing = getById(concert.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        
        updateById(concert);
        log.info("Concert updated: id={}", concert.getId());
        return concert;
    }
    
    @Override
    public List<Concert> getAllConcerts() {
        return list();
    }
    
    @Override
    public List<Concert> getOnSaleConcerts() {
        return list(new LambdaQueryWrapper<Concert>()
                .eq(Concert::getStatus, ConcertStatus.ON_SALE.getCode()));
    }
    
    @Override
    public Concert getConcertById(Long id) {
        Concert concert = getById(id);
        if (concert == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        return concert;
    }
    
    @Override
    public void startSale(Long concertId) {
        Concert concert = getById(concertId);
        if (concert == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        
        concert.setStatus(ConcertStatus.ON_SALE.getCode());
        updateById(concert);
        log.info("Concert sale started: id={}", concertId);
    }
    
    @Override
    public void endSale(Long concertId) {
        Concert concert = getById(concertId);
        if (concert == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        
        concert.setStatus(ConcertStatus.ENDED.getCode());
        updateById(concert);
        log.info("Concert sale ended: id={}", concertId);
    }
    
    @Override
    public void deleteConcert(Long concertId) {
        removeById(concertId);
        log.info("Concert deleted: id={}", concertId);
    }
}

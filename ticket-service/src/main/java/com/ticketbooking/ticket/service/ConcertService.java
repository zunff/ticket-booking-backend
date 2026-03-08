package com.ticketbooking.ticket.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.ticket.entity.Concert;

import java.util.List;

public interface ConcertService extends IService<Concert> {
    
    Concert createConcert(Concert concert);
    
    Concert updateConcert(Concert concert);
    
    List<Concert> getAllConcerts();
    
    Page<Concert> getOnSaleConcerts(Page<Concert> page);
    
    Concert getConcertById(Long id);
    
    void startSale(Long concertId);
    
    void endSale(Long concertId);
    
    void deleteConcert(Long concertId);
}

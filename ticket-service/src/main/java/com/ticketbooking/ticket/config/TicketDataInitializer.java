package com.ticketbooking.ticket.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticketbooking.common.enums.ConcertStatus;
import com.ticketbooking.ticket.entity.Concert;
import com.ticketbooking.ticket.entity.TicketGrade;
import com.ticketbooking.ticket.service.ConcertService;
import com.ticketbooking.ticket.service.TicketGradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketDataInitializer implements ApplicationRunner {
    
    private final ConcertService concertService;
    private final TicketGradeService ticketGradeService;
    
    @Override
    public void run(ApplicationArguments args) {
        log.info("Initializing concert data...");
        
        List<Concert> activeConcerts = concertService.list(
                new LambdaQueryWrapper<Concert>()
                        .eq(Concert::getStatus, ConcertStatus.ON_SALE.getCode())
        );
        
        log.info("Concert data initialization completed. Total concerts: {}", activeConcerts.size());
    }
}

package com.ticketbooking.ticket.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.common.model.dto.TicketGradeDTO;
import com.ticketbooking.ticket.entity.TicketGrade;

import java.util.List;

public interface TicketGradeService extends IService<TicketGrade> {
    
    TicketGrade createTicketGrade(TicketGrade ticketGrade);
    
    TicketGrade updateTicketGrade(TicketGrade ticketGrade);
    
    List<TicketGrade> getGradesByConcertId(Long concertId);
    
    TicketGrade getGradeById(Long id);
    
    void deleteTicketGrade(Long id);
    
    TicketGradeDTO getGradeWithConcertName(Long id);
}

package com.ticketbooking.ticket.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.ticket.entity.TicketGrade;
import com.ticketbooking.ticket.mapper.TicketGradeMapper;
import com.ticketbooking.ticket.service.TicketGradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketGradeServiceImpl extends ServiceImpl<TicketGradeMapper, TicketGrade> implements TicketGradeService {
    
    @Override
    public TicketGrade createTicketGrade(TicketGrade ticketGrade) {
        save(ticketGrade);
        log.info("TicketGrade created: id={}, concertId={}, gradeName={}", 
                ticketGrade.getId(), ticketGrade.getConcertId(), ticketGrade.getGradeName());
        return ticketGrade;
    }
    
    @Override
    public TicketGrade updateTicketGrade(TicketGrade ticketGrade) {
        TicketGrade existing = getById(ticketGrade.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        
        updateById(ticketGrade);
        log.info("TicketGrade updated: id={}", ticketGrade.getId());
        return ticketGrade;
    }
    
    @Override
    public List<TicketGrade> getGradesByConcertId(Long concertId) {
        return baseMapper.findByConcertId(concertId);
    }
    
    @Override
    public TicketGrade getGradeById(Long id) {
        TicketGrade grade = getById(id);
        if (grade == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        return grade;
    }
    
    @Override
    public void deleteTicketGrade(Long id) {
        removeById(id);
        log.info("TicketGrade deleted: id={}", id);
    }
}

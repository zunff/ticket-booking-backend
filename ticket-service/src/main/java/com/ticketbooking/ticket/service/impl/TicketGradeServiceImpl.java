package com.ticketbooking.ticket.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.model.dto.TicketGradeDTO;
import com.ticketbooking.ticket.entity.TicketGrade;
import com.ticketbooking.ticket.mapper.TicketGradeMapper;
import com.ticketbooking.ticket.service.TicketGradeService;
import com.ticketbooking.ticket.service.ConcertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketGradeServiceImpl extends ServiceImpl<TicketGradeMapper, TicketGrade> implements TicketGradeService {
    
    private final ConcertService concertService;
    
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
    
    public TicketGradeDTO getGradeWithConcertName(Long id) {
        TicketGrade grade = getGradeById(id);
        if (grade == null) {
            return null;
        }
        
        TicketGradeDTO dto = new TicketGradeDTO();
        dto.setId(grade.getId());
        dto.setConcertId(grade.getConcertId());
        dto.setGradeName(grade.getGradeName());
        dto.setPrice(grade.getPrice());
        dto.setTotalStock(grade.getTotalStock());
        dto.setIsSelectedSeat(grade.getIsSelectedSeat());
        
        try {
            String concertName = concertService.getById(grade.getConcertId()).getName();
            dto.setConcertName(concertName);
        } catch (Exception e) {
            log.warn("Failed to get concert name for concertId={}", grade.getConcertId());
            dto.setConcertName("");
        }
        
        return dto;
    }
}

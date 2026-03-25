package com.ticketbooking.ticket.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.model.dto.TicketGradeDTO;
import com.ticketbooking.ticket.client.StockServiceClient;
import com.ticketbooking.ticket.entity.Concert;
import com.ticketbooking.ticket.entity.TicketGrade;
import com.ticketbooking.ticket.mapper.TicketGradeMapper;
import com.ticketbooking.ticket.service.TicketGradeService;
import com.ticketbooking.ticket.service.ConcertService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class TicketGradeServiceImpl extends ServiceImpl<TicketGradeMapper, TicketGrade> implements TicketGradeService {

    @Lazy
    @Resource
    private ConcertService concertService;

    @Resource
    private StockServiceClient stockServiceClient;
    
    @Override
    public TicketGrade createTicketGrade(TicketGrade ticketGrade) {
        save(ticketGrade);
        log.info("TicketGrade created: id={}, concertId={}, gradeName={}",
                ticketGrade.getId(), ticketGrade.getConcertId(), ticketGrade.getGradeName());

        // Initialize stock for this grade
        try {
            stockServiceClient.initStock(
                    ticketGrade.getConcertId(),
                    ticketGrade.getId(),
                    ticketGrade.getTotalStock()
            );
            log.info("Stock initialized for new ticket grade: concertId={}, gradeId={}, totalStock={}",
                    ticketGrade.getConcertId(), ticketGrade.getId(), ticketGrade.getTotalStock());
        } catch (Exception e) {
            log.error("Failed to initialize stock for ticket grade: gradeId={}", ticketGrade.getId(), e);
        }

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
            Concert concert = concertService.getById(grade.getConcertId());
            dto.setConcertName(concert.getName());
            dto.setPurchaseLimit(concert.getPurchaseLimit() != null ? concert.getPurchaseLimit() : 1);
        } catch (Exception e) {
            log.warn("Failed to get concert info for concertId={}", grade.getConcertId());
            dto.setConcertName("");
            dto.setPurchaseLimit(1);
        }

        return dto;
    }
}

package com.ticketbooking.ticket.controller;

import com.ticketbooking.common.result.Result;
import com.ticketbooking.ticket.entity.Concert;
import com.ticketbooking.ticket.entity.TicketGrade;
import com.ticketbooking.ticket.service.ConcertService;
import com.ticketbooking.ticket.service.TicketGradeService;
import com.ticketbooking.ticket.model.vo.ConcertVO;
import com.ticketbooking.ticket.model.vo.TicketGradeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/concerts")
@RequiredArgsConstructor
public class ConcertController {
    
    private final ConcertService concertService;
    private final TicketGradeService ticketGradeService;
    
    @GetMapping
    public Result<List<ConcertVO>> getAllConcerts() {
        List<Concert> concerts = concertService.getAllConcerts();
        List<ConcertVO> vos = concerts.stream()
                .map(this::convertConcertToVO)
                .collect(Collectors.toList());
        return Result.success(vos);
    }
    
    @GetMapping("/on-sale")
    public Result<List<ConcertVO>> getOnSaleConcerts() {
        List<Concert> concerts = concertService.getOnSaleConcerts();
        List<ConcertVO> vos = concerts.stream()
                .map(this::convertConcertToVO)
                .collect(Collectors.toList());
        return Result.success(vos);
    }
    
    @GetMapping("/{id}")
    public Result<ConcertVO> getConcertById(@PathVariable Long id) {
        Concert concert = concertService.getConcertById(id);
        return concert != null ? Result.success(convertConcertToVO(concert)) : Result.error(2001, "演唱会不存在");
    }
    
    @GetMapping("/{concertId}/grades")
    public Result<List<TicketGradeVO>> getGradesByConcertId(@PathVariable Long concertId) {
        List<TicketGrade> grades = ticketGradeService.getGradesByConcertId(concertId);
        List<TicketGradeVO> vos = grades.stream()
                .map(this::convertGradeToVO)
                .collect(Collectors.toList());
        return Result.success(vos);
    }
    
    @GetMapping("/grades/{gradeId}")
    public Result<TicketGradeVO> getGradeById(@PathVariable Long gradeId) {
        TicketGrade grade = ticketGradeService.getById(gradeId);
        return grade != null ? Result.success(convertGradeToVO(grade)) : Result.error(2002, "档位不存在");
    }
    
    private ConcertVO convertConcertToVO(Concert concert) {
        ConcertVO vo = new ConcertVO();
        BeanUtils.copyProperties(concert, vo);
        return vo;
    }
    
    private TicketGradeVO convertGradeToVO(TicketGrade grade) {
        TicketGradeVO vo = new TicketGradeVO();
        BeanUtils.copyProperties(grade, vo);
        return vo;
    }
}

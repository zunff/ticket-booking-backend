package com.ticketbooking.ticket.controller;

import com.ticketbooking.common.annotation.RequireAdmin;
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
@RequestMapping("/admin/concerts")
@RequiredArgsConstructor
public class ConcertAdminController {
    
    private final ConcertService concertService;
    private final TicketGradeService ticketGradeService;
    
    @PostMapping
    @RequireAdmin
    public Result<ConcertVO> createConcert(@RequestBody Concert concert) {
        return Result.success("演唱会创建成功", convertConcertToVO(concertService.createConcert(concert)));
    }
    
    @PutMapping("/{id}")
    @RequireAdmin
    public Result<ConcertVO> updateConcert(@PathVariable Long id, @RequestBody Concert concert) {
        concert.setId(id);
        return Result.success("演唱会更新成功", convertConcertToVO(concertService.updateConcert(concert)));
    }
    
    @GetMapping
    @RequireAdmin
    public Result<List<ConcertVO>> getAllConcerts() {
        List<ConcertVO> vos = concertService.getAllConcerts().stream()
                .map(this::convertConcertToVO)
                .collect(Collectors.toList());
        return Result.success(vos);
    }
    
    @GetMapping("/{id}")
    @RequireAdmin
    public Result<ConcertVO> getConcertById(@PathVariable Long id) {
        Concert concert = concertService.getConcertById(id);
        return concert != null ? Result.success(convertConcertToVO(concert)) : Result.error(2001, "演唱会不存在");
    }
    
    @PostMapping("/{id}/start-sale")
    @RequireAdmin
    public Result<String> startSale(@PathVariable Long id) {
        concertService.startSale(id);
        return Result.success("开售成功");
    }
    
    @PostMapping("/{id}/end-sale")
    @RequireAdmin
    public Result<String> endSale(@PathVariable Long id) {
        concertService.endSale(id);
        return Result.success("结束售票成功");
    }
    
    @DeleteMapping("/{id}")
    @RequireAdmin
    public Result<String> deleteConcert(@PathVariable Long id) {
        concertService.deleteConcert(id);
        return Result.success("删除成功");
    }
    
    @PostMapping("/{concertId}/grades")
    @RequireAdmin
    public Result<TicketGradeVO> createGrade(@PathVariable Long concertId, @RequestBody TicketGrade grade) {
        grade.setConcertId(concertId);
        return Result.success("档位创建成功", convertGradeToVO(ticketGradeService.createTicketGrade(grade)));
    }
    
    @GetMapping("/{concertId}/grades")
    @RequireAdmin
    public Result<List<TicketGradeVO>> getGrades(@PathVariable Long concertId) {
        List<TicketGradeVO> vos = ticketGradeService.getGradesByConcertId(concertId).stream()
                .map(this::convertGradeToVO)
                .collect(Collectors.toList());
        return Result.success(vos);
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

package com.ticketbooking.ticket.controller;

import com.ticketbooking.common.annotation.RequireAdmin;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.ticket.converter.ConcertConverter;
import com.ticketbooking.ticket.entity.Concert;
import com.ticketbooking.ticket.entity.TicketGrade;
import com.ticketbooking.ticket.model.vo.ConcertDetailWithStockVO;
import com.ticketbooking.ticket.model.vo.ConcertVO;
import com.ticketbooking.ticket.model.vo.TicketGradeVO;
import com.ticketbooking.ticket.service.ConcertService;
import com.ticketbooking.ticket.service.TicketGradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/concerts")
@RequiredArgsConstructor
public class ConcertAdminController {

    private final ConcertService concertService;
    private final TicketGradeService ticketGradeService;
    private final ConcertConverter concertConverter;

    @PostMapping
    @RequireAdmin
    public Result<ConcertVO> createConcert(@RequestBody Concert concert) {
        return Result.success("演唱会创建成功", concertConverter.toVO(concertService.createConcert(concert)));
    }

    @PutMapping("/{id}")
    @RequireAdmin
    public Result<ConcertVO> updateConcert(@PathVariable Long id, @RequestBody Concert concert) {
        concert.setId(id);
        return Result.success("演唱会更新成功", concertConverter.toVO(concertService.updateConcert(concert)));
    }

    @GetMapping
    @RequireAdmin
    public Result<List<ConcertVO>> getAllConcerts() {
        return Result.success(concertConverter.toVOList(concertService.getAllConcerts()));
    }

    @GetMapping("/{id}")
    @RequireAdmin
    public Result<ConcertDetailWithStockVO> getConcertById(@PathVariable Long id) {
        return Result.success(concertService.getConcertDetailById(id));
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
        return Result.success("档位创建成功", concertConverter.toGradeVO(ticketGradeService.createTicketGrade(grade)));
    }

    @GetMapping("/{concertId}/grades")
    @RequireAdmin
    public Result<List<TicketGradeVO>> getGrades(@PathVariable Long concertId) {
        return Result.success(concertConverter.toGradeVOList(ticketGradeService.getGradesByConcertId(concertId)));
    }
}

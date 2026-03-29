package com.ticketbooking.ticket.controller;

import com.ticketbooking.common.annotation.RequireAuth;
import com.ticketbooking.common.enums.Role;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.ticket.converter.ConcertConverter;
import com.ticketbooking.ticket.entity.TicketGrade;
import com.ticketbooking.ticket.model.qo.ConcertCreateQO;
import com.ticketbooking.ticket.model.qo.ConcertQueryQO;
import com.ticketbooking.ticket.model.qo.ConcertUpdateQO;
import com.ticketbooking.ticket.model.vo.ConcertDetailWithStockVO;
import com.ticketbooking.ticket.model.vo.ConcertVO;
import com.ticketbooking.ticket.model.vo.TicketGradeVO;
import com.ticketbooking.ticket.service.CachePreheatService;
import com.ticketbooking.ticket.service.ConcertService;
import com.ticketbooking.ticket.service.TicketGradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "演唱会管理")
@RestController
@RequestMapping("/admin/concerts")
@RequiredArgsConstructor
public class ConcertAdminController {

    private final ConcertService concertService;
    private final TicketGradeService ticketGradeService;
    private final ConcertConverter concertConverter;
    private final CachePreheatService cachePreheatService;

    @Operation(summary = "创建演唱会")
    @PostMapping
    @RequireAuth(Role.ADMIN)
    public Result<ConcertVO> createConcert(@RequestBody ConcertCreateQO qo) {
        return Result.success("演唱会创建成功", concertService.createConcert(qo));
    }

    @Operation(summary = "更新演唱会")
    @PutMapping("/{id}")
    @RequireAuth(Role.ADMIN)
    public Result<ConcertVO> updateConcert(@PathVariable Long id, @RequestBody ConcertUpdateQO qo) {
        return Result.success("演唱会更新成功", concertService.updateConcert(id, qo));
    }

    @Operation(summary = "演唱会列表")
    @GetMapping
    @RequireAuth(Role.ADMIN)
    public Result<PageResult<ConcertVO>> getAllConcerts(ConcertQueryQO qo) {
        return Result.success(concertService.getConcertsForAdmin(qo));
    }

    @Operation(summary = "演唱会详情")
    @GetMapping("/{id}")
    @RequireAuth(Role.ADMIN)
    public Result<ConcertDetailWithStockVO> getConcertById(@PathVariable Long id) {
        return Result.success(concertService.getConcertDetailById(id));
    }

    @Operation(summary = "删除演唱会")
    @DeleteMapping("/{id}")
    @RequireAuth(Role.ADMIN)
    public Result<String> deleteConcert(@PathVariable Long id) {
        concertService.deleteConcert(id);
        return Result.success("删除成功");
    }

    @Operation(summary = "添加票档")
    @PostMapping("/{concertId}/grades")
    @RequireAuth(Role.ADMIN)
    public Result<TicketGradeVO> createGrade(@PathVariable Long concertId, @RequestBody TicketGrade grade) {
        grade.setConcertId(concertId);
        return Result.success("档位创建成功", concertConverter.toGradeVO(ticketGradeService.createTicketGrade(grade)));
    }

    @Operation(summary = "票档列表")
    @GetMapping("/{concertId}/grades")
    @RequireAuth(Role.ADMIN)
    public Result<java.util.List<TicketGradeVO>> getGrades(@PathVariable Long concertId) {
        return Result.success(concertConverter.toGradeVOList(ticketGradeService.getGradesByConcertId(concertId)));
    }

    @Operation(summary = "缓存预热")
    @PostMapping("/{concertId}/preheat")
    @RequireAuth(Role.ADMIN)
    public Result<String> preheatCache(@PathVariable Long concertId) {
        cachePreheatService.preheatConcertCache(concertId);
        return Result.success("缓存预热成功");
    }
}

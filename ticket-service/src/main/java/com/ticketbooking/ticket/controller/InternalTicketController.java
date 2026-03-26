package com.ticketbooking.ticket.controller;

import com.ticketbooking.common.model.dto.TicketGradeDTO;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.ticket.service.ConcertService;
import com.ticketbooking.ticket.service.TicketGradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalTicketController {

    private final TicketGradeService ticketGradeService;
    private final ConcertService concertService;

    @GetMapping("/grades/{id}")
    public Result<TicketGradeDTO> getGradeById(@PathVariable Long id) {
        return Result.success(ticketGradeService.getGradeWithConcertName(id));
    }
}

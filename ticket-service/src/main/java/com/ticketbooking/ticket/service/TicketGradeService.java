package com.ticketbooking.ticket.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.common.model.dto.TicketGradeDTO;
import com.ticketbooking.ticket.entity.TicketGrade;

import java.util.List;

public interface TicketGradeService extends IService<TicketGrade> {

    TicketGrade createTicketGrade(TicketGrade ticketGrade);

    List<TicketGrade> getGradesByConcertId(Long concertId);

    /**
     * 获取演唱会票价档位（带缓存）
     */
    List<TicketGrade> getGradesByConcertIdWithCache(Long concertId);

    TicketGrade getGradeById(Long id);

    TicketGradeDTO getGradeWithConcertName(Long id);

}

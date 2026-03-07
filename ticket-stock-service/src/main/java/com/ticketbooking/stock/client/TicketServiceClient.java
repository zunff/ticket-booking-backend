package com.ticketbooking.stock.client;

import com.ticketbooking.stock.model.dto.ConcertDTO;
import com.ticketbooking.stock.model.dto.TicketGradeDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "ticket-service", path = "/api")
public interface TicketServiceClient {
    
    @GetMapping("/concerts/{id}")
    ConcertDTO getConcertById(@PathVariable("id") Long id);
    
    @GetMapping("/concerts/{concertId}/grades")
    List<TicketGradeDTO> getGradesByConcertId(@PathVariable("concertId") Long concertId);
    
    @GetMapping("/grades/{gradeId}")
    TicketGradeDTO getGradeById(@PathVariable("gradeId") Long gradeId);
}

package com.ticketbooking.ticket.converter;

import com.ticketbooking.ticket.entity.Concert;
import com.ticketbooking.ticket.entity.TicketGrade;
import com.ticketbooking.ticket.model.vo.ConcertVO;
import com.ticketbooking.ticket.model.vo.ConcertDetailVO;
import com.ticketbooking.ticket.model.vo.ConcertDetailWithStockVO;
import com.ticketbooking.ticket.model.vo.TicketGradeVO;
import com.ticketbooking.ticket.model.vo.TicketGradeWithStockVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ConcertConverter {

    /**
     * 根据时间计算状态文本
     * - 在售卖时间内（start_sale_time ~ end_sale_time）：开售中
     * - 其他情况：已关闭
     */
    private String calculateStatusText(Concert concert) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startSaleTime = concert.getStartSaleTime();
        LocalDateTime endSaleTime = concert.getEndSaleTime();

        if (!now.isBefore(startSaleTime) && now.isBefore(endSaleTime)) {
            return "开售中";
        } else {
            return "已关闭";
        }
    }

    public ConcertVO toVO(Concert concert) {
        if (concert == null) {
            return null;
        }
        ConcertVO vo = new ConcertVO();
        vo.setId(concert.getId());
        vo.setName(concert.getName());
        vo.setVenue(concert.getVenue());
        vo.setShowTime(concert.getShowTime());
        vo.setStartSaleTime(concert.getStartSaleTime());
        vo.setEndSaleTime(concert.getEndSaleTime());
        vo.setStatus(concert.getStatus());
        vo.setStatusText(calculateStatusText(concert));
        vo.setCreatedAt(concert.getCreatedAt());
        return vo;
    }
    
    public List<ConcertVO> toVOList(List<Concert> concerts) {
        return concerts.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }
    
    public TicketGradeVO toGradeVO(TicketGrade grade) {
        if (grade == null) {
            return null;
        }
        TicketGradeVO vo = new TicketGradeVO();
        vo.setId(grade.getId());
        vo.setConcertId(grade.getConcertId());
        vo.setGradeName(grade.getGradeName());
        vo.setPrice(grade.getPrice());
        vo.setTotalStock(grade.getTotalStock());
        vo.setIsSelectedSeat(grade.getIsSelectedSeat());
        return vo;
    }
    
    public List<TicketGradeVO> toGradeVOList(List<TicketGrade> grades) {
        return grades.stream()
                .map(this::toGradeVO)
                .collect(Collectors.toList());
    }
    
    public ConcertDetailVO toDetailVO(Concert concert, List<TicketGrade> grades) {
        if (concert == null) {
            return null;
        }
        ConcertDetailVO vo = new ConcertDetailVO();
        vo.setId(concert.getId());
        vo.setName(concert.getName());
        vo.setVenue(concert.getVenue());
        vo.setShowTime(concert.getShowTime());
        vo.setStartSaleTime(concert.getStartSaleTime());
        vo.setEndSaleTime(concert.getEndSaleTime());
        vo.setStatus(concert.getStatus());
        vo.setGrades(toGradeVOList(grades));
        return vo;
    }
    
    public ConcertDetailWithStockVO toDetailWithStockVO(Concert concert, List<TicketGrade> grades, Map<Long, Integer> stockMap) {
        if (concert == null) {
            return null;
        }
        ConcertDetailWithStockVO vo = new ConcertDetailWithStockVO();
        vo.setId(concert.getId());
        vo.setName(concert.getName());
        vo.setVenue(concert.getVenue());
        vo.setShowTime(concert.getShowTime());
        vo.setStartSaleTime(concert.getStartSaleTime());
        vo.setEndSaleTime(concert.getEndSaleTime());
        vo.setStatus(concert.getStatus());
        vo.setStatusText(calculateStatusText(concert));

        List<TicketGradeWithStockVO> gradeWithStockList = grades.stream()
                .map(grade -> {
                    TicketGradeWithStockVO gradeVO = new TicketGradeWithStockVO();
                    gradeVO.setId(grade.getId());
                    gradeVO.setConcertId(grade.getConcertId());
                    gradeVO.setGradeName(grade.getGradeName());
                    gradeVO.setPrice(grade.getPrice());
                    gradeVO.setTotalStock(grade.getTotalStock());
                    gradeVO.setIsSelectedSeat(grade.getIsSelectedSeat());
                    // If stock record doesn't exist, use totalStock as initial value
                    Integer availableStock = stockMap.get(grade.getId());
                    gradeVO.setAvailableStock(availableStock != null ? availableStock : grade.getTotalStock());
                    return gradeVO;
                })
                .collect(Collectors.toList());

        vo.setGrades(gradeWithStockList);
        return vo;
    }
}

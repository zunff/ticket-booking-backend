package com.ticketbooking.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.enums.ConcertStatus;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.ticket.client.StockServiceClient;
import com.ticketbooking.ticket.converter.ConcertConverter;
import com.ticketbooking.ticket.entity.Concert;
import com.ticketbooking.ticket.entity.TicketGrade;
import com.ticketbooking.ticket.mapper.ConcertMapper;
import com.ticketbooking.ticket.model.qo.ConcertQueryQO;
import com.ticketbooking.ticket.model.vo.ConcertDetailWithStockVO;
import com.ticketbooking.ticket.model.vo.ConcertVO;
import com.ticketbooking.ticket.service.ConcertService;
import com.ticketbooking.ticket.service.TicketGradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertServiceImpl extends ServiceImpl<ConcertMapper, Concert> implements ConcertService {

    private final ConcertConverter concertConverter;
    private final TicketGradeService ticketGradeService;
    private final StockServiceClient stockServiceClient;

    @Override
    public Concert createConcert(Concert concert) {
        concert.setStatus(ConcertStatus.PENDING.getCode());
        save(concert);
        log.info("Concert created: id={}, name={}", concert.getId(), concert.getName());
        return concert;
    }

    @Override
    public Concert updateConcert(Concert concert) {
        Concert existing = getById(concert.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        updateById(concert);
        log.info("Concert updated: id={}", concert.getId());
        return concert;
    }

    @Override
    public List<Concert> getAllConcerts() {
        return list();
    }

    @Override
    public List<ConcertVO> getConcertVOList(List<Concert> concerts) {
        return concertConverter.toVOList(concerts);
    }

    @Override
    public PageResult<ConcertVO> getConcerts(ConcertQueryQO qo) {
        LambdaQueryWrapper<Concert> wrapper = new LambdaQueryWrapper<>();

        // 名称模糊查询
        if (qo.getName() != null && !qo.getName().trim().isEmpty()) {
            wrapper.like(Concert::getName, qo.getName().trim());
        }

        // 状态筛选
        if (qo.getStatus() != null) {
            wrapper.eq(Concert::getStatus, qo.getStatus());
        }

        // 按演出时间降序排序
        wrapper.orderByDesc(Concert::getShowTime);

        Page<Concert> page = page(new Page<>(qo.getCurrent(), qo.getSize()), wrapper);

        // 转换为 VO
        List<ConcertVO> voList = concertConverter.toVOList(page.getRecords());

        return PageResult.of(
                voList,
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }

    @Override
    public ConcertDetailWithStockVO getConcertDetailById(Long id) {
        Concert concert = getById(id);
        if (concert == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        List<TicketGrade> grades = ticketGradeService.getGradesByConcertId(id);

        List<StockDTO> stocks = stockServiceClient.getStocksByConcertId(id);
        Map<Long, Integer> stockMap = stocks.stream()
                .collect(Collectors.toMap(StockDTO::getGradeId, StockDTO::getAvailableStock));

        return concertConverter.toDetailWithStockVO(concert, grades, stockMap);
    }

    @Override
    public Concert getConcertById(Long id) {
        Concert concert = getById(id);
        if (concert == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        return concert;
    }

    @Override
    public void startSale(Long concertId) {
        Concert concert = getById(concertId);
        if (concert == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        concert.setStatus(ConcertStatus.ON_SALE.getCode());
        updateById(concert);
        log.info("Concert sale started: id={}", concertId);
    }

    @Override
    public void endSale(Long concertId) {
        Concert concert = getById(concertId);
        if (concert == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        concert.setStatus(ConcertStatus.ENDED.getCode());
        updateById(concert);
        log.info("Concert sale ended: id={}", concertId);
    }

    @Override
    public void deleteConcert(Long concertId) {
        removeById(concertId);
        log.info("Concert deleted: id={}", concertId);
    }
}

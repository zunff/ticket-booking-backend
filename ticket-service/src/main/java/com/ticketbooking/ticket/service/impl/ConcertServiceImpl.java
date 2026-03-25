package com.ticketbooking.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.enums.ConcertStatus;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.ticket.client.OrderServiceClient;
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
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConcertServiceImpl extends ServiceImpl<ConcertMapper, Concert> implements ConcertService {

    @Resource
    private ConcertConverter concertConverter;

    @Resource
    private TicketGradeService ticketGradeService;

    @Resource
    private StockServiceClient stockServiceClient;

    @Resource
    private OrderServiceClient orderServiceClient;

    @Override
    public Concert createConcert(Concert concert) {
        // 状态根据时间动态计算，默认为已关闭
        concert.setStatus(ConcertStatus.CLOSED.getCode());
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
    public PageResult<ConcertVO> getConcerts(ConcertQueryQO qo) {
        LambdaQueryWrapper<Concert> wrapper = new LambdaQueryWrapper<>();

        // 名称模糊查询
        if (qo.getName() != null && !qo.getName().trim().isEmpty()) {
            wrapper.like(Concert::getName, qo.getName().trim());
        }

        // 基于时间的动态状态筛选
        if (qo.getTimeStatus() != null) {
            LocalDateTime now = LocalDateTime.now();
            switch (qo.getTimeStatus()) {
                case 0: // 已关闭
                    wrapper.eq(Concert::getStatus, 0);
                    break;
                case 1: // 开售中：在售票时间内
                    wrapper.ne(Concert::getStatus, 0)
                            .le(Concert::getStartSaleTime, now)
                            .gt(Concert::getEndSaleTime, now);
                    break;
                case 2: // 即将开售：还没到开始售票时间
                    wrapper.ne(Concert::getStatus, 0)
                            .gt(Concert::getStartSaleTime, now);
                    break;
                case 3: // 已结束：已过结束售票时间
                    wrapper.eq(Concert::getStatus, 0)
                            .le(Concert::getEndSaleTime, now);
                    break;
                default:
                    // 不筛选状态，只排除已关闭的
                    wrapper.ne(Concert::getStatus, 0);
                    break;
            }
        } else {
            // 默认只显示非已关闭的演唱会
            wrapper.ne(Concert::getStatus, 0);
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
        return getConcertDetailById(id, null);
    }

    @Override
    public ConcertDetailWithStockVO getConcertDetailById(Long id, Long userId) {
        Concert concert = getById(id);
        if (concert == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        List<TicketGrade> grades = ticketGradeService.getGradesByConcertId(id);

        List<StockDTO> stocks = stockServiceClient.getStocksByConcertId(id);
        Map<Long, Integer> stockMap = stocks.stream()
                .collect(Collectors.toMap(StockDTO::getGradeId, StockDTO::getAvailableStock));

        ConcertDetailWithStockVO vo = concertConverter.toDetailWithStockVO(concert, grades, stockMap);

        // 设置限购数量
        int purchaseLimit = concert.getPurchaseLimit() != null ? concert.getPurchaseLimit() : 1;
        vo.setPurchaseLimit(purchaseLimit);

        // 查询用户购买数量
        if (userId != null) {
            try {
                int purchasedCount = orderServiceClient.countUserPurchased(userId, id);
                vo.setUserPurchasedCount(purchasedCount);
                vo.setCanPurchase(purchasedCount < purchaseLimit);
            } catch (Exception e) {
                log.warn("Failed to get user purchased count: userId={}, concertId={}", userId, id, e);
                vo.setUserPurchasedCount(0);
                vo.setCanPurchase(true);
            }
        } else {
            vo.setUserPurchasedCount(0);
            vo.setCanPurchase(true);
        }

        return vo;
    }

    @Override
    public void deleteConcert(Long concertId) {
        removeById(concertId);
        log.info("Concert deleted: id={}", concertId);
    }
}

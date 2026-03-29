package com.ticketbooking.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.cache.MultiLevelCacheService;
import com.ticketbooking.common.constant.CacheConstant;
import com.ticketbooking.common.constant.RedisExpireConstants;
import com.ticketbooking.common.constant.RedisKeyConstants;
import com.ticketbooking.common.enums.ConcertStatus;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.model.dto.ConcertDTO;
import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.common.utils.RedisUtils;
import com.ticketbooking.ticket.client.OrderServiceClient;
import com.ticketbooking.ticket.client.StockServiceClient;
import com.ticketbooking.ticket.client.XxlJobAdminClient;
import com.ticketbooking.ticket.converter.ConcertConverter;
import com.ticketbooking.ticket.entity.Concert;
import com.ticketbooking.ticket.entity.TicketGrade;
import com.ticketbooking.ticket.mapper.ConcertMapper;
import com.ticketbooking.ticket.model.qo.ConcertCreateQO;
import com.ticketbooking.ticket.model.qo.ConcertQueryQO;
import com.ticketbooking.ticket.model.qo.ConcertUpdateQO;
import com.ticketbooking.ticket.model.vo.ConcertDetailWithStockVO;
import com.ticketbooking.ticket.model.vo.ConcertVO;
import com.ticketbooking.ticket.service.ConcertService;
import com.ticketbooking.ticket.service.TicketGradeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConcertServiceImpl extends ServiceImpl<ConcertMapper, Concert> implements ConcertService {

    private static final int PREHEAT_ADVANCE_MINUTES = 5;
    private static final String PREHEAT_JOB_HANDLER = "concertCachePreheat";

    @Resource
    private ConcertConverter concertConverter;

    @Resource
    private TicketGradeService ticketGradeService;

    @Resource
    private StockServiceClient stockServiceClient;

    @Resource
    private OrderServiceClient orderServiceClient;

    @Resource
    private XxlJobAdminClient xxlJobAdminClient;

    @Resource
    private MultiLevelCacheService cacheService;

    @Resource
    private RedisUtils redisUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createConcert(ConcertCreateQO qo) {
        // 创建演唱会
        Concert concert = new Concert();
        concert.setName(qo.getName());
        concert.setVenue(qo.getVenue());
        concert.setShowTime(qo.getShowTime());
        concert.setStartSaleTime(qo.getStartSaleTime());
        concert.setEndSaleTime(qo.getEndSaleTime());
        concert.setPurchaseLimit(qo.getPurchaseLimit());
        concert.setStatus(ConcertStatus.CLOSED.getCode());
        save(concert);

        log.info("Concert created: id={}, name={}", concert.getId(), concert.getName());

        // 创建票档
        if (qo.getGrades() != null && !qo.getGrades().isEmpty()) {
            for (ConcertCreateQO.TicketGradeQO gradeQO : qo.getGrades()) {
                TicketGrade grade = new TicketGrade();
                grade.setConcertId(concert.getId());
                grade.setGradeName(gradeQO.getGradeName());
                grade.setPrice(gradeQO.getPrice());
                grade.setTotalStock(gradeQO.getTotalStock());
                grade.setIsSelectedSeat(gradeQO.getIsSelectedSeat());
                ticketGradeService.createTicketGrade(grade);
            }
            log.info("Created {} grades for concertId={}", qo.getGrades().size(), concert.getId());
        }

        // 创建预热任务
        schedulePreheatJob(concert);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConcert(Long id, ConcertUpdateQO qo) {
        Concert concert = getById(id);
        if (concert == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        // 更新演唱会信息
        if (qo.getName() != null) {
            concert.setName(qo.getName());
        }
        if (qo.getVenue() != null) {
            concert.setVenue(qo.getVenue());
        }
        if (qo.getShowTime() != null) {
            concert.setShowTime(qo.getShowTime());
        }
        if (qo.getStartSaleTime() != null) {
            concert.setStartSaleTime(qo.getStartSaleTime());
        }
        if (qo.getEndSaleTime() != null) {
            concert.setEndSaleTime(qo.getEndSaleTime());
        }
        if (qo.getPurchaseLimit() != null) {
            concert.setPurchaseLimit(qo.getPurchaseLimit());
        }
        if (qo.getStatus() != null) {
            concert.setStatus(qo.getStatus());
        }
        updateById(concert);

        log.info("Concert updated: id={}", id);

        // 更新票档（全量更新）
        if (qo.getGrades() != null) {
            updateGrades(id, qo.getGrades());
        }

        // 清除缓存
        clearConcertCache(id);

        // 如果开售时间有变化，更新预热任务
        if (qo.getStartSaleTime() != null) {
            schedulePreheatJob(concert);
        }
    }

    /**
     * 更新票档（全量更新）
     */
    private void updateGrades(Long concertId, List<ConcertUpdateQO.TicketGradeQO> gradeQOs) {
        // 获取现有票档
        List<TicketGrade> existingGrades = ticketGradeService.getGradesByConcertId(concertId);
        Map<Long, TicketGrade> existingMap = existingGrades.stream()
                .collect(Collectors.toMap(TicketGrade::getId, g -> g));

        for (ConcertUpdateQO.TicketGradeQO gradeQO : gradeQOs) {
            if (gradeQO.getId() != null && existingMap.containsKey(gradeQO.getId())) {
                // 更新现有票档
                TicketGrade grade = existingMap.get(gradeQO.getId());
                if (gradeQO.getGradeName() != null) {
                    grade.setGradeName(gradeQO.getGradeName());
                }
                if (gradeQO.getPrice() != null) {
                    grade.setPrice(gradeQO.getPrice());
                }
                if (gradeQO.getTotalStock() != null) {
                    grade.setTotalStock(gradeQO.getTotalStock());
                }
                if (gradeQO.getIsSelectedSeat() != null) {
                    grade.setIsSelectedSeat(gradeQO.getIsSelectedSeat());
                }
                ticketGradeService.updateById(grade);
                log.info("Grade updated: gradeId={}", grade.getId());
            } else {
                // 新增票档
                TicketGrade grade = new TicketGrade();
                grade.setConcertId(concertId);
                grade.setGradeName(gradeQO.getGradeName());
                grade.setPrice(gradeQO.getPrice());
                grade.setTotalStock(gradeQO.getTotalStock());
                grade.setIsSelectedSeat(gradeQO.getIsSelectedSeat() != null ? gradeQO.getIsSelectedSeat() : 0);
                ticketGradeService.createTicketGrade(grade);
                log.info("Grade created: gradeId={}, concertId={}", grade.getId(), concertId);
            }
        }
    }

    /**
     * 清除演唱会相关缓存
     */
    private void clearConcertCache(Long concertId) {
        // 清除演唱会缓存
        String cacheKey = String.valueOf(concertId);
        String redisKey = RedisKeyConstants.buildConcertInfoKey(concertId);
        cacheService.evict(CacheConstant.CACHE_CONCERT, cacheKey, redisKey);
        log.info("演唱会缓存已清除: concertId={}", concertId);

        // 清除票价档位缓存
        cacheService.evictByPattern(
                CacheConstant.CACHE_TICKET_GRADE,
                String.valueOf(concertId),
                RedisKeyConstants.buildTicketGradeKey(concertId)
        );

        // 清除限购数量缓存
        String limitKey = RedisKeyConstants.buildConcertLimitKey(concertId);
        redisUtils.delete(limitKey);
        log.info("限购数量缓存已清除: key={}", limitKey);
    }

    @Override
    public List<Concert> getAllConcerts() {
        return list();
    }

    @Override
    public PageResult<ConcertVO> getConcerts(ConcertQueryQO qo) {
        return getConcertsInternal(qo, true);
    }

    @Override
    public PageResult<ConcertVO> getConcertsForAdmin(ConcertQueryQO qo) {
        return getConcertsInternal(qo, false);
    }

    /**
     * 分页查询演唱会列表
     *
     * @param qo 查询条件
     * @param onlyOnSale 是否只查开售中（用户端为 true，管理端为 false）
     */
    private PageResult<ConcertVO> getConcertsInternal(ConcertQueryQO qo, boolean onlyOnSale) {
        LambdaQueryWrapper<Concert> wrapper = new LambdaQueryWrapper<>();

        if (qo.getName() != null && !qo.getName().trim().isEmpty()) {
            wrapper.like(Concert::getName, qo.getName().trim());
        }

        LocalDateTime now = LocalDateTime.now();

        if (onlyOnSale) {
            // 用户端：只查开售中的演唱会
            wrapper.ne(Concert::getStatus, 0)
                    .le(Concert::getStartSaleTime, now)
                    .gt(Concert::getEndSaleTime, now);
        } else {
            // 管理端：根据 timeStatus 筛选
            if (qo.getTimeStatus() != null) {
                switch (qo.getTimeStatus()) {
                    case 0: // 已关闭
                        wrapper.eq(Concert::getStatus, 0);
                        break;
                    case 1: // 开售中
                        wrapper.ne(Concert::getStatus, 0)
                                .le(Concert::getStartSaleTime, now)
                                .gt(Concert::getEndSaleTime, now);
                        break;
                    case 2: // 即将开售
                        wrapper.ne(Concert::getStatus, 0)
                                .gt(Concert::getStartSaleTime, now);
                        break;
                    case 3: // 已结束
                        wrapper.ne(Concert::getStatus, 0)
                                .le(Concert::getEndSaleTime, now);
                        break;
                    default:
                        break;
                }
            }
            // 管理端不传 timeStatus 则查所有
        }

        wrapper.orderByDesc(Concert::getShowTime);

        Page<Concert> page = page(new Page<>(qo.getCurrent(), qo.getSize()), wrapper);
        List<ConcertVO> voList = concertConverter.toVOList(page.getRecords());

        return PageResult.of(voList, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ConcertDetailWithStockVO getConcertDetailById(Long id) {
        return getConcertDetailById(id, null);
    }

    @Override
    public ConcertDetailWithStockVO getConcertDetailById(Long id, Long userId) {
        String cacheKey = String.valueOf(id);
        String redisKey = RedisKeyConstants.buildConcertInfoKey(id);

        Concert concert = cacheService.get(
                CacheConstant.CACHE_CONCERT,
                cacheKey,
                Concert.class,
                redisKey,
                RedisExpireConstants.PREHEAT_CACHE_HOURS * 3600,
                () -> {
                    Concert c = getById(id);
                    if (c == null) {
                        throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
                    }
                    return c;
                }
        );

        List<TicketGrade> grades = ticketGradeService.getGradesByConcertIdWithCache(id);

        List<StockDTO> stocks = stockServiceClient.getStocksByConcertId(id);
        Map<Long, Integer> stockMap = stocks.stream()
                .collect(Collectors.toMap(StockDTO::getGradeId, StockDTO::getAvailableStock));

        ConcertDetailWithStockVO vo = concertConverter.toDetailWithStockVO(concert, grades, stockMap);

        int purchaseLimit = concert.getPurchaseLimit() != null ? concert.getPurchaseLimit() : 1;
        vo.setPurchaseLimit(purchaseLimit);

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
        Concert concert = getById(concertId);
        if (concert != null) {
            removePreheatJob(concert);
        }
        removeById(concertId);
        log.info("Concert deleted: id={}", concertId);
    }

    @Override
    public ConcertDTO getConcertDTOById(Long id) {
        Concert concert = getById(id);
        if (concert == null) {
            return null;
        }

        ConcertDTO dto = new ConcertDTO();
        dto.setId(concert.getId());
        dto.setName(concert.getName());
        dto.setVenue(concert.getVenue());
        dto.setShowTime(concert.getShowTime());
        dto.setStartSaleTime(concert.getStartSaleTime());
        dto.setEndSaleTime(concert.getEndSaleTime());
        dto.setPurchaseLimit(concert.getPurchaseLimit() != null ? concert.getPurchaseLimit() : 1);
        dto.setStatus(concert.getStatus());

        return dto;
    }

    private void schedulePreheatJob(Concert concert) {
        if (concert.getStartSaleTime() == null) {
            log.debug("演唱会没有设置开售时间，跳过预热任务: concertId={}", concert.getId());
            return;
        }

        LocalDateTime preheatTime = concert.getStartSaleTime().minusMinutes(PREHEAT_ADVANCE_MINUTES);
        LocalDateTime now = LocalDateTime.now();

        if (preheatTime.isBefore(now)) {
            log.debug("预热时间已过，跳过任务创建: concertId={}, preheatTime={}", concert.getId(), preheatTime);
            return;
        }

        try {
            Integer jobId = xxlJobAdminClient.addOrUpdateOnceJob(
                    PREHEAT_JOB_HANDLER,
                    String.valueOf(concert.getId()),
                    preheatTime,
                    concert.getPreheatJobId()  // 从数据库读取现有的 jobId
            );
            if (jobId != null) {
                // 更新数据库中的 jobId
                concert.setPreheatJobId(jobId);
                updateById(concert);
                log.info("预热任务创建/更新成功: concertId={}, preheatTime={}, jobId={}",
                        concert.getId(), preheatTime, jobId);
            } else {
                log.warn("预热任务创建/更新失败: concertId={}", concert.getId());
            }
        } catch (Exception e) {
            log.error("预热任务调度异常: concertId={}", concert.getId(), e);
        }
    }

    private void removePreheatJob(Concert concert) {
        if (concert.getPreheatJobId() == null) {
            log.debug("演唱会没有预热任务: concertId={}", concert.getId());
            return;
        }
        try {
            boolean success = xxlJobAdminClient.removeJob(concert.getPreheatJobId());
            if (success) {
                log.info("预热任务删除成功: concertId={}, jobId={}", concert.getId(), concert.getPreheatJobId());
            }
        } catch (Exception e) {
            log.warn("删除预热任务异常: concertId={}", concert.getId(), e);
        }
    }
}

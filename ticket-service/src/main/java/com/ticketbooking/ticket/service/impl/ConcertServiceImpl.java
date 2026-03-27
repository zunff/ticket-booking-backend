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
import com.ticketbooking.ticket.client.OrderServiceClient;
import com.ticketbooking.ticket.client.StockServiceClient;
import com.ticketbooking.ticket.client.XxlJobAdminClient;
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

    /**
     * 预热提前时间（分钟）
     */
    private static final int PREHEAT_ADVANCE_MINUTES = 5;

    /**
     * 预热任务 JobHandler 名称
     */
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

    @Override
    public Concert createConcert(Concert concert) {
        // 状态根据时间动态计算，默认为已关闭
        concert.setStatus(ConcertStatus.CLOSED.getCode());
        save(concert);
        log.info("Concert created: id={}, name={}", concert.getId(), concert.getName());

        // 创建预热任务
        schedulePreheatJob(concert);

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

        // 清除演唱会缓存
        String cacheKey = String.valueOf(concert.getId());
        String redisKey = RedisKeyConstants.buildConcertInfoKey(concert.getId());
        cacheService.evict(CacheConstant.CACHE_CONCERT, cacheKey, redisKey);
        log.info("演唱会缓存已清除: concertId={}", concert.getId());

        // 清除票价档位缓存
        cacheService.evictByPattern(
                CacheConstant.CACHE_TICKET_GRADE,
                String.valueOf(concert.getId()),
                RedisKeyConstants.buildTicketGradeKey(concert.getId())
        );

        // 如果开售时间有变化，更新预热任务
        if (concert.getStartSaleTime() != null &&
            (existing.getStartSaleTime() == null ||
             !existing.getStartSaleTime().equals(concert.getStartSaleTime()))) {
            schedulePreheatJob(concert);
        }

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
        // 演唱会基本信息使用多级缓存（不含库存）
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

        // 票价档位使用多级缓存
        List<TicketGrade> grades = ticketGradeService.getGradesByConcertIdWithCache(id);

        // 库存信息 - 只从 Redis 获取，不加 Caffeine（高频更新）
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
        // 删除预热任务
        removePreheatJob(concertId);

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

    /**
     * 调度预热任务
     */
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
                    preheatTime
            );
            if (jobId != null) {
                log.info("预热任务创建/更新成功: concertId={}, preheatTime={}, jobId={}",
                        concert.getId(), preheatTime, jobId);
            } else {
                log.warn("预热任务创建/更新失败: concertId={}", concert.getId());
            }
        } catch (Exception e) {
            log.error("预热任务调度异常: concertId={}", concert.getId(), e);
        }
    }

    /**
     * 删除预热任务
     */
    private void removePreheatJob(Long concertId) {
        try {
            boolean success = xxlJobAdminClient.removeJob(String.valueOf(concertId));
            if (success) {
                log.info("预热任务删除成功: concertId={}", concertId);
            }
        } catch (Exception e) {
            log.warn("删除预热任务异常: concertId={}", concertId, e);
        }
    }
}

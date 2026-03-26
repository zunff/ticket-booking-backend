package com.ticketbooking.ticket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.constant.RedisKeyConstants;
import com.ticketbooking.common.model.dto.ConcertDTO;
import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.common.utils.RedisUtils;
import com.ticketbooking.ticket.client.StockServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 缓存预热服务
 * 负责在演唱会开售前预热 Redis 缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CachePreheatService {

    private final StockServiceClient stockServiceClient;
    private final ConcertService concertService;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    /**
     * 缓存过期时间：24 小时
     */
    private static final long CACHE_EXPIRE_HOURS = 24;

    /**
     * 预热演唱会缓存
     * 包括：演唱会限购、演唱会信息、各档位库存
     *
     * @param concertId 演唱会ID
     */
    public void preheatConcertCache(Long concertId) {
        log.info("[缓存预热] 开始: concertId={}", concertId);

        try {
            // 1. 获取演唱会信息
            ConcertDTO concert = concertService.getConcertDTOById(concertId);
            if (concert == null) {
                log.warn("[缓存预热] 演唱会不存在: concertId={}", concertId);
                return;
            }

            // 2. 预热演唱会限购数量 (CONCERT_LIMIT_KEY)
            preheatConcertLimit(concertId, concert.getPurchaseLimit());

            // 3. 预热演唱会信息 (CONCERT_INFO_KEY)
            preheatConcertInfo(concert);

            // 4. 预热各档位库存 (TICKET_STOCK_KEY) - 直接查询并写入
            preheatStockCache(concertId);

            log.info("[缓存预热] 完成: concertId={}, name={}", concertId, concert.getName());

        } catch (Exception e) {
            log.error("[缓存预热] 失败: concertId={}", concertId, e);
            throw new RuntimeException("缓存预热失败: " + e.getMessage(), e);
        }
    }

    /**
     * 预热演唱会限购数量
     */
    private void preheatConcertLimit(Long concertId, Integer purchaseLimit) {
        String limitKey = RedisKeyConstants.buildConcertLimitKey(concertId);
        int limit = purchaseLimit != null ? purchaseLimit : 1;
        redisUtils.set(limitKey, String.valueOf(limit), CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        log.debug("[缓存预热] 限购数量: key={}, value={}", limitKey, limit);
    }

    /**
     * 预热演唱会信息
     */
    private void preheatConcertInfo(ConcertDTO concert) {
        String infoKey = RedisKeyConstants.buildConcertInfoKey(concert.getId());
        try {
            String concertJson = objectMapper.writeValueAsString(concert);
            redisUtils.set(infoKey, concertJson, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("[缓存预热] 演唱会信息: key={}", infoKey);
        } catch (JsonProcessingException e) {
            log.error("[缓存预热] 序列化失败: concertId={}", concert.getId(), e);
            throw new RuntimeException("序列化演唱会信息失败", e);
        }
    }

    /**
     * 预热各档位库存
     * 直接通过 Feign 查询库存信息并写入 Redis
     */
    private void preheatStockCache(Long concertId) {
        List<StockDTO> stocks = stockServiceClient.getStocksByConcertId(concertId);
        if (stocks == null || stocks.isEmpty()) {
            log.warn("[缓存预热] 演唱会没有库存数据: concertId={}", concertId);
            return;
        }

        int successCount = 0;
        for (StockDTO stock : stocks) {
            try {
                String stockKey = RedisKeyConstants.buildTicketStockKey(concertId, stock.getGradeId());
                redisUtils.set(stockKey, String.valueOf(stock.getAvailableStock()), CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                successCount++;
            } catch (Exception e) {
                log.error("[缓存预热] 写入库存失败: concertId={}, gradeId={}", concertId, stock.getGradeId(), e);
            }
        }

        log.info("[缓存预热] 库存预热完成: concertId={}, 成功={}/{}", concertId, successCount, stocks.size());
    }
}

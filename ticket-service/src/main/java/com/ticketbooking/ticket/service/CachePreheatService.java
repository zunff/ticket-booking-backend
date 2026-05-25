package com.ticketbooking.ticket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.constant.RedisExpireConstants;
import com.ticketbooking.common.constant.RedisKeyConstants;
import com.ticketbooking.common.model.dto.ConcertDTO;
import com.ticketbooking.common.model.dto.StockDTO;
import com.ticketbooking.common.utils.RedisUtils;
import com.ticketbooking.ticket.client.StockServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

            // 2. 基于售票结束时间计算 TTL，确保整个售票期间缓存不过期
            long ttlSeconds = calculateSaleTtl(concert.getEndSaleTime());
            log.info("[缓存预热] TTL计算: concertId={}, endSaleTime={}, ttlSeconds={}", concertId, concert.getEndSaleTime(), ttlSeconds);

            // 3. 预热演唱会限购数量 (CONCERT_LIMIT_KEY)
            preheatConcertLimit(concertId, concert.getPurchaseLimit(), ttlSeconds);

            // 4. 预热演唱会信息 (CONCERT_INFO_KEY)
            preheatConcertInfo(concert, ttlSeconds);

            // 5. 预热各档位库存 (TICKET_STOCK_KEY)
            preheatStockCache(concertId, ttlSeconds);

            log.info("[缓存预热] 完成: concertId={}, name={}, ttl={}h", concertId, concert.getName(), ttlSeconds / 3600);

        } catch (Exception e) {
            log.error("[缓存预热] 失败: concertId={}", concertId, e);
            throw new RuntimeException("缓存预热失败: " + e.getMessage(), e);
        }
    }

    /**
     * 基于售票结束时间计算缓存 TTL
     * 如果 endSaleTime 为空或已过期，使用默认 24h
     */
    private long calculateSaleTtl(LocalDateTime endSaleTime) {
        if (endSaleTime == null) {
            return RedisExpireConstants.PREHEAT_CACHE_SECONDS;
        }
        long seconds = Duration.between(LocalDateTime.now(), endSaleTime).getSeconds();
        return Math.max(seconds, 3600);
    }

    /**
     * 预热演唱会限购数量
     */
    private void preheatConcertLimit(Long concertId, Integer purchaseLimit, long ttlSeconds) {
        String limitKey = RedisKeyConstants.buildConcertLimitKey(concertId);
        int limit = purchaseLimit != null ? purchaseLimit : 1;
        redisUtils.set(limitKey, String.valueOf(limit), ttlSeconds, TimeUnit.SECONDS);
        log.debug("[缓存预热] 限购数量: key={}, value={}, ttl={}s", limitKey, limit, ttlSeconds);
    }

    /**
     * 预热演唱会信息
     */
    private void preheatConcertInfo(ConcertDTO concert, long ttlSeconds) {
        String infoKey = RedisKeyConstants.buildConcertInfoKey(concert.getId());
        try {
            String concertJson = objectMapper.writeValueAsString(concert);
            redisUtils.set(infoKey, concertJson, ttlSeconds, TimeUnit.SECONDS);
            log.debug("[缓存预热] 演唱会信息: key={}, ttl={}s", infoKey, ttlSeconds);
        } catch (JsonProcessingException e) {
            log.error("[缓存预热] 序列化失败: concertId={}", concert.getId(), e);
            throw new RuntimeException("序列化演唱会信息失败", e);
        }
    }

    /**
     * 预热各档位库存
     * 使用 Hash 结构批量写入，一个演唱会一个 Hash Key
     */
    private void preheatStockCache(Long concertId, long ttlSeconds) {
        List<StockDTO> stocks = stockServiceClient.getStocksByConcertId(concertId);
        if (stocks == null || stocks.isEmpty()) {
            log.warn("[缓存预热] 演唱会没有库存数据: concertId={}", concertId);
            return;
        }

        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(concertId);
        Map<String, String> stockMap = stocks.stream()
                .collect(Collectors.toMap(
                        s -> String.valueOf(s.getGradeId()),
                        s -> String.valueOf(s.getAvailableStock())
                ));

        redisUtils.hMSet(stockHashKey, stockMap);
        redisUtils.expire(stockHashKey, ttlSeconds, TimeUnit.SECONDS);

        log.info("[缓存预热] 库存预热完成: concertId={}, 档位数={}, ttl={}s", concertId, stocks.size(), ttlSeconds);
    }
}

package com.ticketbooking.ticket.job;

import com.ticketbooking.ticket.service.CachePreheatService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 缓存预热定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CachePreheatJob {

    private final CachePreheatService cachePreheatService;

    /**
     * 演唱会缓存预热任务
     * JobHandler: concertCachePreheat
     * 参数: concertId (演唱会ID)
     */
    @XxlJob("concertCachePreheat")
    public void preheatConcertCache() {
        String param = XxlJobHelper.getJobParam();
        log.info("执行演唱会缓存预热任务, 参数: {}", param);

        if (param == null || param.trim().isEmpty()) {
            XxlJobHelper.handleFail("任务参数为空，需要提供 concertId");
            return;
        }

        try {
            Long concertId = Long.parseLong(param.trim());
            cachePreheatService.preheatConcertCache(concertId);
            XxlJobHelper.handleSuccess("预热完成: concertId=" + concertId);
        } catch (NumberFormatException e) {
            XxlJobHelper.handleFail("无效的 concertId: " + param);
        } catch (Exception e) {
            log.error("演唱会缓存预热任务执行失败", e);
            XxlJobHelper.handleFail("预热失败: " + e.getMessage());
        }
    }
}

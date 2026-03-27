package com.ticketbooking.common.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.SystemException;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Redis 操作 Sentinel blockHandler
 * 统一处理限流/熔断异常，转换为系统异常抛出（纳入熔断统计）
 */
@Slf4j
public final class RedisBlockHandler {

    private RedisBlockHandler() {}

    /**
     * 统一 blockHandler - 根据 BlockException 具体类型抛出对应异常
     * 使用 SystemException 以便纳入 Sentinel 熔断统计
     */
    public static Object handle(BlockException e) throws SystemException {
        String resource = e.getRule() != null ? e.getRule().getResource() : "unknown";
        log.warn("Redis blocked: resource={}, type={}", resource, e.getClass().getSimpleName());

        if (e instanceof FlowException || e instanceof ParamFlowException) {
            throw new SystemException(ErrorCode.RATE_LIMITED);
        }

        if (e instanceof DegradeException) {
            throw new SystemException(ErrorCode.SERVICE_DEGRADED);
        }

        if (e instanceof AuthorityException) {
            throw new SystemException(ErrorCode.FORBIDDEN);
        }

        // 兜底
        throw new SystemException(ErrorCode.SERVICE_DEGRADED);
    }

    // ==================== String 操作 blockHandler ====================

    public static void handleSet(BlockException e) {
        handle(e);
    }

    public static void handleSetWithTimeout(BlockException e) {
        handle(e);
    }

    public static void handleSetEx(BlockException e) {
        handle(e);
    }

    public static String handleGet(BlockException e) {
        handle(e);
        return null;
    }

    public static Boolean handleDelete(BlockException e) {
        handle(e);
        return null;
    }

    public static Boolean handleHasKey(BlockException e) {
        handle(e);
        return null;
    }

    public static Long handleDecrement(BlockException e) {
        handle(e);
        return null;
    }

    public static Long handleDecrementBy(BlockException e) {
        handle(e);
        return null;
    }

    public static Long handleIncrement(BlockException e) {
        handle(e);
        return null;
    }

    public static Long handleIncrementBy(BlockException e) {
        handle(e);
        return null;
    }

    public static Boolean handleSetIfAbsent(BlockException e) {
        handle(e);
        return null;
    }

    public static Boolean handleSetNx(BlockException e) {
        handle(e);
        return null;
    }

    public static Long handleGetExpire(BlockException e) {
        handle(e);
        return null;
    }

    public static Boolean handleExpire(BlockException e) {
        handle(e);
        return null;
    }

    // ==================== Lua 脚本 blockHandler ====================

    public static Long handleExecuteLua(BlockException e) {
        handle(e);
        return null;
    }

    public static <T> T handleExecuteLuaGeneric(BlockException e) {
        handle(e);
        return null;
    }

    // ==================== Hash 操作 blockHandler ====================

    public static void handleHSet(BlockException e) {
        handle(e);
    }

    public static String handleHGet(BlockException e) {
        handle(e);
        return null;
    }

    public static Map<Object, Object> handleHGetAll(BlockException e) {
        handle(e);
        return null;
    }

    public static void handleHMSet(BlockException e) {
        handle(e);
    }

    public static Long handleHIncrBy(BlockException e) {
        handle(e);
        return null;
    }

    public static Boolean handleHExists(BlockException e) {
        handle(e);
        return null;
    }
}

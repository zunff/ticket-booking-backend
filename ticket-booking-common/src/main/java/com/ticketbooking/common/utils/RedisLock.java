package com.ticketbooking.common.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLock {

    private final RedisUtils redisUtils;

    private static final long DEFAULT_EXPIRE_SECONDS = 30;
    private static final long RETRY_SLEEP_MILLIS = 50;

    /**
     * 尝试获取锁并执行任务
     *
     * @param lockKey   锁的 key
     * @param requestId 请求标识（用于释放锁时校验）
     * @param supplier  要执行的任务
     * @return 任务执行结果，获取锁失败返回 null
     */
    public <T> T tryLockAndExecute(String lockKey, String requestId, Supplier<T> supplier) {
        return tryLockAndExecute(lockKey, requestId, DEFAULT_EXPIRE_SECONDS, TimeUnit.SECONDS, supplier);
    }

    /**
     * 尝试获取锁并执行任务（带超时）
     *
     * @param lockKey   锁的 key
     * @param requestId 请求标识
     * @param expire    锁过期时间
     * @param unit      时间单位
     * @param supplier  要执行的任务
     * @return 任务执行结果，获取锁失败返回 null
     */
    public <T> T tryLockAndExecute(String lockKey, String requestId, long expire, TimeUnit unit, Supplier<T> supplier) {
        boolean locked = false;
        try {
            locked = tryLock(lockKey, requestId, expire, unit);
            if (!locked) {
                log.warn("Failed to acquire lock: {}", lockKey);
                return null;
            }
            return supplier.get();
        } finally {
            if (locked) {
                unlock(lockKey, requestId);
            }
        }
    }

    /**
     * 尝试获取锁
     */
    public boolean tryLock(String lockKey, String requestId, long expire, TimeUnit unit) {
        Boolean success = redisUtils.setIfAbsent(lockKey, requestId, expire, unit);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁（Lua 脚本保证原子性）
     */
    public void unlock(String lockKey, String requestId) {
        String script = "if redis.call(\"get\", KEYS[1]) == ARGV[1] then " +
                        "    return redis.call(\"del\", KEYS[1]) " +
                        "else " +
                        "    return 0 " +
                        "end";
        redisUtils.executeLuaScript(script, List.of(lockKey), requestId);
    }
}

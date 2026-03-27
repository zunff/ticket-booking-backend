package com.ticketbooking.common.utils;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.ticketbooking.common.sentinel.RedisBlockHandler;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类 - 带 Sentinel 熔断限流保护
 */
@Component
public class RedisUtils {

    private static final String RESOURCE_PREFIX = "redis:";

    private final StringRedisTemplate redisTemplate;

    public RedisUtils(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ==================== String 操作 ====================

    @SentinelResource(value = RESOURCE_PREFIX + "set", blockHandler = "handleSet", blockHandlerClass = RedisBlockHandler.class)
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "setWithTimeout", blockHandler = "handleSetWithTimeout", blockHandlerClass = RedisBlockHandler.class)
    public void set(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "setEx", blockHandler = "handleSetEx", blockHandlerClass = RedisBlockHandler.class)
    public void setEx(String key, String value, long seconds) {
        redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "get", blockHandler = "handleGet", blockHandlerClass = RedisBlockHandler.class)
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "delete", blockHandler = "handleDelete", blockHandlerClass = RedisBlockHandler.class)
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "hasKey", blockHandler = "handleHasKey", blockHandlerClass = RedisBlockHandler.class)
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "decrement", blockHandler = "handleDecrement", blockHandlerClass = RedisBlockHandler.class)
    public Long decrement(String key) {
        return redisTemplate.opsForValue().decrement(key);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "decrementBy", blockHandler = "handleDecrementBy", blockHandlerClass = RedisBlockHandler.class)
    public Long decrement(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "increment", blockHandler = "handleIncrement", blockHandlerClass = RedisBlockHandler.class)
    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "incrementBy", blockHandler = "handleIncrementBy", blockHandlerClass = RedisBlockHandler.class)
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "setIfAbsent", blockHandler = "handleSetIfAbsent", blockHandlerClass = RedisBlockHandler.class)
    public Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "setNx", blockHandler = "handleSetNx", blockHandlerClass = RedisBlockHandler.class)
    public Boolean setNx(String key, String value) {
        return redisTemplate.opsForValue().setIfAbsent(key, value);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "getExpire", blockHandler = "handleGetExpire", blockHandlerClass = RedisBlockHandler.class)
    public Long getExpire(String key, TimeUnit unit) {
        return redisTemplate.getExpire(key, unit);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "expire", blockHandler = "handleExpire", blockHandlerClass = RedisBlockHandler.class)
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    // ==================== Lua 脚本操作 ====================

    @SentinelResource(value = RESOURCE_PREFIX + "executeLua", blockHandler = "handleExecuteLua", blockHandlerClass = RedisBlockHandler.class)
    public Long executeLuaScript(String script, List<String> keys, String... args) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        return redisTemplate.execute(redisScript, keys, args);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "executeLuaGeneric", blockHandler = "handleExecuteLuaGeneric", blockHandlerClass = RedisBlockHandler.class)
    public <T> T executeLuaScript(DefaultRedisScript<T> script, List<String> keys, Object... args) {
        return redisTemplate.execute(script, keys, args);
    }

    // ==================== Hash 操作 ====================

    @SentinelResource(value = RESOURCE_PREFIX + "hSet", blockHandler = "handleHSet", blockHandlerClass = RedisBlockHandler.class)
    public void hSet(String key, String field, String value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "hGet", blockHandler = "handleHGet", blockHandlerClass = RedisBlockHandler.class)
    public String hGet(String key, String field) {
        Object value = redisTemplate.opsForHash().get(key, field);
        return value != null ? value.toString() : null;
    }

    @SentinelResource(value = RESOURCE_PREFIX + "hGetAll", blockHandler = "handleHGetAll", blockHandlerClass = RedisBlockHandler.class)
    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "hMSet", blockHandler = "handleHMSet", blockHandlerClass = RedisBlockHandler.class)
    public void hMSet(String key, Map<String, String> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "hIncrBy", blockHandler = "handleHIncrBy", blockHandlerClass = RedisBlockHandler.class)
    public Long hIncrBy(String key, String field, long delta) {
        return redisTemplate.opsForHash().increment(key, field, delta);
    }

    @SentinelResource(value = RESOURCE_PREFIX + "hExists", blockHandler = "handleHExists", blockHandlerClass = RedisBlockHandler.class)
    public Boolean hExists(String key, String field) {
        return redisTemplate.opsForHash().hasKey(key, field);
    }
}

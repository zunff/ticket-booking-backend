package com.ticketbooking.order.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 抢票 Lua 脚本配置
 */
@Getter
@Component
public class BookingLuaScript {

    private DefaultRedisScript<Long> bookingScript;

    /**
     * 演唱会级别限购 Lua 脚本 (Hash 结构版本)
     *
     * KEYS[1]: stockHashKey    - 库存 Hash key (ticket:stock:{concertId})
     * KEYS[2]: userPurchaseKey - 用户购买数量 key (user:concert:purchase:{concertId}:{userId})
     * KEYS[3]: limitKey        - 限购数量 key (concert:limit:{concertId})
     *
     * ARGV[1]: userId          - 用户ID
     * ARGV[2]: quantity        - 购买数量
     * ARGV[3]: gradeId         - 档位ID (作为 Hash field)
     * ARGV[4]: expireSeconds   - userPurchaseKey 过期时间（秒）
     *
     * 返回值说明：
     *  1: 成功
     * -2: 票务不存在
     * -3: 库存不足
     * -4: 限购配置不存在
     * -5: 超出限购
     */
    private static final String LUA_SCRIPT =
            "local stockHashKey = KEYS[1]\n" +
            "local userPurchaseKey = KEYS[2]\n" +
            "local limitKey = KEYS[3]\n" +
            "local userId = ARGV[1]\n" +
            "local quantity = tonumber(ARGV[2])\n" +
            "local gradeId = ARGV[3]\n" +
            "local expireSeconds = tonumber(ARGV[4])\n" +
            "\n" +
            "-- 1. 检查限购配置\n" +
            "local limit = tonumber(redis.call('GET', limitKey))\n" +
            "if limit == nil or limit == 0 then\n" +
            "    return -4\n" +
            "end\n" +
            "\n" +
            "-- 2. 检查用户已购买数量\n" +
            "local bought = tonumber(redis.call('GET', userPurchaseKey) or '0')\n" +
            "if bought + quantity > limit then\n" +
            "    return -5\n" +
            "end\n" +
            "\n" +
            "-- 3. 检查库存 (Hash 操作)\n" +
            "local stock = tonumber(redis.call('HGET', stockHashKey, gradeId))\n" +
            "if stock == nil then\n" +
            "    return -2\n" +
            "end\n" +
            "if stock < quantity then\n" +
            "    return -3\n" +
            "end\n" +
            "\n" +
            "-- 4. 执行扣减：库存减少，用户购买数增加\n" +
            "redis.call('HINCRBY', stockHashKey, gradeId, -quantity)\n" +
            "redis.call('INCRBY', userPurchaseKey, quantity)\n" +
            "redis.call('EXPIRE', userPurchaseKey, expireSeconds)\n" +
            "\n" +
            "return 1\n";

    @PostConstruct
    public void init() {
        bookingScript = new DefaultRedisScript<>();
        bookingScript.setScriptText(LUA_SCRIPT);
        bookingScript.setResultType(Long.class);
    }

    /**
     * 返回码描述
     */
    public static String getResultDesc(long code) {
        return switch ((int) code) {
            case 1 -> "成功";
            case -2 -> "票务不存在";
            case -3 -> "库存不足";
            case -4 -> "限购配置不存在";
            case -5 -> "超出限购";
            default -> "未知错误";
        };
    }
}

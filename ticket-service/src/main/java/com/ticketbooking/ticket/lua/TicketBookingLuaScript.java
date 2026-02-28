package com.ticketbooking.ticket.lua;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class TicketBookingLuaScript {
    
    private DefaultRedisScript<Long> bookingScript;
    
    private static final String LUA_SCRIPT = 
            "local stockKey = KEYS[1]\n" +
            "local userTicketKey = KEYS[2]\n" +
            "local userId = ARGV[1]\n" +
            "local quantity = tonumber(ARGV[2])\n" +
            "\n" +
            "if redis.call('EXISTS', userTicketKey) == 1 then\n" +
            "    return -1\n" +
            "end\n" +
            "\n" +
            "local stock = tonumber(redis.call('GET', stockKey))\n" +
            "if stock == nil then\n" +
            "    return -2\n" +
            "end\n" +
            "\n" +
            "if stock < quantity then\n" +
            "    return -3\n" +
            "end\n" +
            "\n" +
            "redis.call('DECRBY', stockKey, quantity)\n" +
            "\n" +
            "redis.call('SET', userTicketKey, '1', 'EX', 86400)\n" +
            "\n" +
            "return 1\n";
    
    @PostConstruct
    public void init() {
        bookingScript = new DefaultRedisScript<>();
        bookingScript.setScriptText(LUA_SCRIPT);
        bookingScript.setResultType(Long.class);
    }
    
    public DefaultRedisScript<Long> getBookingScript() {
        return bookingScript;
    }
}

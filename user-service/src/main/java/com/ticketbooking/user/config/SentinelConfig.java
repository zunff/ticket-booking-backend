package com.ticketbooking.user.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class SentinelConfig {
    
    static {
        System.setProperty("csp.sentinel.log.dir", System.getProperty("java.io.tmpdir") + "/sentinel/logs");
    }
}

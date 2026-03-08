package com.ticketbooking.stock.config;

import com.ticketbooking.common.feign.FeignErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign Client配置
 */
@Configuration
public class FeignClientConfig {

    @Bean
    public FeignErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }
}

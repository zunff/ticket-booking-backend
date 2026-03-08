package com.ticketbooking.common.feign;

import org.springframework.context.annotation.Bean;

/**
 * Feign配置
 * 注意：此类不使用@Configuration注解，避免在不使用Feign的服务中被扫描到
 * 使用时请在各个服务的配置类中通过@Bean方式创建FeignErrorDecoder
 */
public class FeignConfig {

    @Bean
    public FeignErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }
}

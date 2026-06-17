package com.ticketbooking.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.feign.FeignErrorDecoder;
import com.ticketbooking.common.feign.FeignResultDecoder;
import feign.codec.Decoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientConfig {

    @Bean
    public FeignErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }

    @Bean
    public Decoder feignDecoder(ObjectMapper objectMapper) {
        return new FeignResultDecoder(objectMapper);
    }
}

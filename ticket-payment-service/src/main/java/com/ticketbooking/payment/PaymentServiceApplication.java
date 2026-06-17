package com.ticketbooking.payment;

import com.ticketbooking.payment.strategy.alipay.AlipayProperties;
import com.ticketbooking.payment.strategy.mock.MockProperties;
import com.ticketbooking.payment.strategy.wechat.WechatPayProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties({WechatPayProperties.class, AlipayProperties.class, MockProperties.class})
@MapperScan("com.ticketbooking.payment.mapper")
@ComponentScan(basePackages = {"com.ticketbooking.common", "com.ticketbooking.payment"})
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}

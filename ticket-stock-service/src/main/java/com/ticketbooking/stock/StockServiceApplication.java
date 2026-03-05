package com.ticketbooking.stock;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.ticketbooking.stock.mapper")
@ComponentScan(basePackages = {"com.ticketbooking.common", "com.ticketbooking.stock"})
@EnableFeignClients(basePackages = "com.ticketbooking.stock.client")
public class StockServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(StockServiceApplication.class, args);
    }
}

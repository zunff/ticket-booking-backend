package com.ticketbooking.common.controller;

import com.ticketbooking.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommonController {
    
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("OK");
    }
}

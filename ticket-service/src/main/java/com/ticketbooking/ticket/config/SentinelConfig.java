package com.ticketbooking.ticket.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SentinelConfig {
    
    static {
        System.setProperty("csp.sentinel.log.dir", System.getProperty("java.io.tmpdir") + "/sentinel/logs");
    }
    
    @Value("${spring.cloud.sentinel.enabled:true}")
    private boolean sentinelEnabled;
    
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }
    
    @PostConstruct
    public void initFlowRules() {
        if (!sentinelEnabled) {
            return;
        }
        
        List<FlowRule> rules = new ArrayList<>();
        
        FlowRule bookTicketRule = new FlowRule();
        bookTicketRule.setResource("bookTicket");
        bookTicketRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        bookTicketRule.setCount(500);
        rules.add(bookTicketRule);
        
        FlowRuleManager.loadRules(rules);
    }
}

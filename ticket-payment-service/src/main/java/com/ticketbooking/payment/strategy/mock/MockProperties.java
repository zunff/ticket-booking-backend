package com.ticketbooking.payment.strategy.mock;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "payment.mock")
public class MockProperties {

    private boolean enabled = true;
}

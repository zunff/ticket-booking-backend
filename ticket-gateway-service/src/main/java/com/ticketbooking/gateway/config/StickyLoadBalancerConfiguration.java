package com.ticketbooking.gateway.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Sticky Session 负载均衡配置
 * 为所有服务启用基于 User-Id 的会话粘性
 */
@Configuration
@LoadBalancerClients(defaultConfiguration = StickyLoadBalancerConfiguration.class)
public class StickyLoadBalancerConfiguration {

    @Bean
    public ReactorServiceInstanceLoadBalancer reactorServiceInstanceLoadBalancer(
            Environment environment,
            LoadBalancerClientFactory factory
    ) {
        String serviceId = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);

        ObjectProvider<ServiceInstanceListSupplier> supplierProvider =
                factory.getLazyProvider(serviceId, ServiceInstanceListSupplier.class);

        return new UserIdStickyLoadBalancer(serviceId, supplierProvider);
    }
}

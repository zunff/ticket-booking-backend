package com.ticketbooking.gateway.config;

import com.ticketbooking.common.constant.JwtConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 User-Id 的 Sticky Session 负载均衡器
 * 确保同一用户的请求路由到同一服务实例
 * 未登录用户使用随机负载均衡
 */
@Slf4j
public class UserIdStickyLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private final String serviceId;
    private final Random random = new Random();

    /**
     * 用户-实例索引映射
     * Key: userId, Value: 实例索引
     */
    private final ConcurrentHashMap<String, Integer> userInstanceMap = new ConcurrentHashMap<>();

    public UserIdStickyLoadBalancer(String serviceId) {
        this.serviceId = serviceId;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        List<ServiceInstance> instances = (List<ServiceInstance>) request.getContext();

        if (instances == null || instances.isEmpty()) {
            log.warn("[Sticky LoadBalancer] 没有可用实例: serviceId={}", serviceId);
            return Mono.just(new EmptyResponse());
        }

        // 从请求中获取用户 ID
        String userId = extractUserId(request);

        if (userId != null && !userId.isEmpty()) {
            // 已登录用户：基于 userId 进行一致性哈希选择实例（Sticky Session）
            int instanceIndex = getStickyInstanceIndex(userId, instances.size());
            ServiceInstance instance = instances.get(instanceIndex);
            log.debug("[Sticky Session] 用户路由: userId={}, serviceId={}, instance={}",
                    userId, serviceId, instance.getUri());
            return Mono.just(new DefaultResponse(instance));
        }

        // 未登录用户（无需登录的公开接口）：使用随机负载均衡
        int randomIndex = random.nextInt(instances.size());
        ServiceInstance instance = instances.get(randomIndex);
        log.debug("[Random] 随机路由: serviceId={}, instance={}", serviceId, instance.getUri());
        return Mono.just(new DefaultResponse(instance));
    }

    /**
     * 从请求中提取用户 ID
     */
    private String extractUserId(Request request) {
        Object context = request.getContext();
        if (context instanceof RequestData) {
            RequestData requestData = (RequestData) context;
            HttpHeaders headers = requestData.getHeaders();
            return headers.getFirst(JwtConstants.HEADER_USER_ID);
        }
        return null;
    }

    /**
     * 获取 Sticky 实例索引
     * 使用一致性哈希确保同一用户总是路由到同一实例
     */
    private int getStickyInstanceIndex(String userId, int instanceCount) {
        // 先从缓存获取
        Integer cachedIndex = userInstanceMap.get(userId);
        if (cachedIndex != null && cachedIndex < instanceCount) {
            return cachedIndex;
        }

        // 使用 hashCode 取模
        int index = Math.abs(userId.hashCode()) % instanceCount;
        userInstanceMap.put(userId, index);
        return index;
    }
}

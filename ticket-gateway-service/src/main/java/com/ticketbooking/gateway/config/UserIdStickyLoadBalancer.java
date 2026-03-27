package com.ticketbooking.gateway.config;

import com.ticketbooking.common.constant.JwtConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

@Slf4j
public class UserIdStickyLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    /**
     * 虚拟节点数：越大分布越均匀，但 ring 构建更慢
     */
    private static final int VIRTUAL_NODES = 30;

    private final String serviceId;
    private final ObjectProvider<ServiceInstanceListSupplier> supplierProvider;
    private final ReactorServiceInstanceLoadBalancer roundRobinDelegate;

    /**
     * hash -> instanceKey
     */
    private volatile NavigableMap<Long, String> hashRing = new TreeMap<>();
    /**
     * instances signature，用于判断是否需要重建 ring
     */
    private volatile int instancesSignature = 0;

    public UserIdStickyLoadBalancer(
            String serviceId,
            ObjectProvider<ServiceInstanceListSupplier> supplierProvider
    ) {
        this.serviceId = serviceId;
        this.supplierProvider = supplierProvider;
        // 用同一个 supplierProvider 构造 RR（未登录流量直接交给它）
        this.roundRobinDelegate = new RoundRobinLoadBalancer(supplierProvider, serviceId);
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        String userId = extractUserId(request);

        // 未登录：直接 RR（正确的 reactive 姿势）
        if (userId == null || userId.isBlank()) {
            return roundRobinDelegate.choose(request);
        }

        // 登录：自己拿实例列表做 sticky
        ServiceInstanceListSupplier supplier = supplierProvider.getIfAvailable();
        if (supplier == null) {
            log.warn("[Sticky LB] no ServiceInstanceListSupplier, serviceId={}", serviceId);
            return Mono.just(new EmptyResponse());
        }

        return supplier.get(request)
                .next()
                .map(instances -> {
                    if (instances == null || instances.isEmpty()) {
                        log.warn("[Sticky LB] no instances, serviceId={}", serviceId);
                        return new EmptyResponse();
                    }

                    rebuildRingIfNeeded(instances);

                    ServiceInstance chosen = chooseSticky(instances, userId);
                    if (chosen == null) {
                        return new EmptyResponse();
                    }

                    return new DefaultResponse(chosen);
                })
                .onErrorResume(ex -> {
                    log.warn("[Sticky LB] get instances failed, serviceId={}", serviceId, ex);
                    return Mono.just(new EmptyResponse());
                });
    }

    /**
     * 从 Gateway 的 Request 里提取 userId（优先兼容 Gateway 的 RequestDataContext）
     */
    private String extractUserId(Request request) {
        Object ctx = request.getContext();

        // Spring Cloud Gateway 常见：RequestDataContext
        if (ctx instanceof RequestDataContext rdc) {
            RequestData rd = rdc.getClientRequest();
            HttpHeaders headers = rd.getHeaders();
            return headers.getFirst(JwtConstants.HEADER_USER_ID);
        }

        // 有些情况下 context 直接是 RequestData
        if (ctx instanceof RequestData rd) {
            return rd.getHeaders().getFirst(JwtConstants.HEADER_USER_ID);
        }

        return null;
    }

    private void rebuildRingIfNeeded(List<ServiceInstance> instances) {
        int sig = computeSignature(instances);
        if (sig == instancesSignature && !hashRing.isEmpty()) return;

        synchronized (this) {
            if (sig == instancesSignature && !hashRing.isEmpty()) return;

            this.hashRing = buildRing(instances);
            this.instancesSignature = sig;

            if (log.isInfoEnabled()) {
                log.info("[Sticky LB] ring rebuilt, serviceId={}, instances={}", serviceId, instances.size());
            }
        }
    }

    private int computeSignature(List<ServiceInstance> instances) {
        // 使用稳定 instanceKey 排序后 hash，避免仅仅顺序变化导致误判
        return instances.stream()
                .map(this::instanceKey)
                .sorted()
                .toList()
                .hashCode();
    }

    private NavigableMap<Long, String> buildRing(List<ServiceInstance> instances) {
        TreeMap<Long, String> ring = new TreeMap<>();
        for (ServiceInstance ins : instances) {
            String key = instanceKey(ins);
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                long h = hash64(key + "#VN#" + i);
                // 处理极小概率碰撞
                while (ring.containsKey(h)) {
                    h++;
                }
                ring.put(h, key);
            }
        }
        return ring;
    }

    private ServiceInstance chooseSticky(List<ServiceInstance> instances, String userId) {
        NavigableMap<Long, String> ring = this.hashRing;
        if (ring == null || ring.isEmpty()) {
            return instances.get(0);
        }

        long h = hash64(userId);

        var entry = ring.ceilingEntry(h);
        if (entry == null) entry = ring.firstEntry();

        String chosenKey = entry.getValue();
        for (ServiceInstance ins : instances) {
            if (chosenKey.equals(instanceKey(ins))) {
                return ins;
            }
        }

        // ring 可能还没来得及更新或实例刚下线：兜底
        int idx = Math.floorMod(h, instances.size());
        return instances.get(idx);
    }

    private String instanceKey(ServiceInstance instance) {
        // 优先 instanceId（在 Nacos/Eureka 环境通常更稳定）
        String id = instance.getInstanceId();
        if (id != null && !id.isBlank()) return id;

        // 退化为 host:port
        return instance.getHost() + ":" + instance.getPort();
    }

    private static final ThreadLocal<MessageDigest> MD5 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    });

    private long hash64(String key) {
        MessageDigest md = MD5.get();
        md.reset();
        byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));

        long h = 0;
        for (int i = 0; i < 8; i++) {
            h = (h << 8) | (digest[i] & 0xff);
        }
        return h & Long.MAX_VALUE;
    }
}

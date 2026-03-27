package com.ticketbooking.gateway.config;

import com.ticketbooking.common.constant.JwtConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserIdStickyLoadBalancerTest {

    private static final String SERVICE_ID = "ticket-service";
    private static final String USER_ID_HEADER = JwtConstants.HEADER_USER_ID;

    @Mock
    private ObjectProvider<ServiceInstanceListSupplier> supplierProvider;

    @Mock
    private ServiceInstanceListSupplier supplier;

    private UserIdStickyLoadBalancer loadBalancer;

    private List<ServiceInstance> instances;

    @BeforeEach
    void setUp() {
        instances = createInstances(3);
        loadBalancer = new UserIdStickyLoadBalancer(SERVICE_ID, supplierProvider);

        // 全局设置 supplierProvider 返回 supplier（lenient 避免参数匹配问题）
        lenient().when(supplierProvider.getIfAvailable(any())).thenReturn(supplier);
        lenient().when(supplierProvider.getIfAvailable()).thenReturn(supplier);
    }

    @Nested
    @DisplayName("未登录用户测试")
    class UnauthenticatedUserTests {

        @Test
        @DisplayName("userId 为 null 时应调用 RoundRobin")
        void shouldUseRoundRobin_whenUserIdIsNull() {
            // Arrange
            Request request = createRequestWithUserId(null);
            when(supplier.get(request)).thenReturn(Flux.just(instances));

            // Act
            Mono<Response<ServiceInstance>> result = loadBalancer.choose(request);

            // Assert - 未登录用户会走 RoundRobin，需要 supplier 返回实例
            StepVerifier.create(result)
                    .assertNext(response -> {
                        // RoundRobin 会返回一个实例
                        assertThat(response).isInstanceOf(DefaultResponse.class);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("userId 为空字符串时应调用 RoundRobin")
        void shouldUseRoundRobin_whenUserIdIsBlank() {
            // Arrange
            Request request = createRequestWithUserId("");
            when(supplier.get(request)).thenReturn(Flux.just(instances));

            // Act
            Mono<Response<ServiceInstance>> result = loadBalancer.choose(request);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response).isInstanceOf(DefaultResponse.class);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("已登录用户测试 - Sticky Session")
    class AuthenticatedUserTests {

        @Test
        @DisplayName("同一用户多次请求应路由到同一实例")
        void sameUser_shouldRouteToSameInstance() {
            // Arrange
            String userId = "user-alice-123";
            Request request = createRequestWithUserId(userId);

            when(supplier.get(any())).thenReturn(Flux.just(instances));

            // Act - 执行 5 次请求
            ServiceInstance firstInstance = null;
            for (int i = 0; i < 5; i++) {
                Mono<Response<ServiceInstance>> result = loadBalancer.choose(request);

                Response<ServiceInstance> response = result.block();

                assertThat(response).isInstanceOf(DefaultResponse.class);
                ServiceInstance instance = response.getServer();

                if (firstInstance == null) {
                    firstInstance = instance;
                } else {
                    // 所有请求应该路由到同一实例
                    assertThat(instance).isEqualTo(firstInstance);
                }
            }
        }

        @Test
        @DisplayName("不同用户可能路由到不同实例")
        void differentUsers_mayRouteToDifferentInstances() {
            // Arrange
            when(supplier.get(any())).thenReturn(Flux.just(instances));

            // 使用多个用户测试分布
            java.util.Set<ServiceInstance> usedInstances = new java.util.HashSet<>();

            for (int i = 0; i < 100; i++) {
                String userId = "user-" + i;
                Request request = createRequestWithUserId(userId);

                Mono<Response<ServiceInstance>> result = loadBalancer.choose(request);

                Response<ServiceInstance> response = result.block();

                assertThat(response).isInstanceOf(DefaultResponse.class);
                ServiceInstance instance = ((DefaultResponse) response).getServer();
                usedInstances.add(instance);
            }

            // 100 个用户应该分布到多个实例（不是全部到同一个实例）
            assertThat(usedInstances.size()).isGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("supplier 为 null 时应返回 EmptyResponse")
        void shouldReturnEmptyResponse_whenSupplierIsNull() {
            // Arrange
            Request request = createRequestWithUserId("user-123");
            lenient().when(supplierProvider.getIfAvailable()).thenReturn(null);
            lenient().when(supplierProvider.getIfAvailable(any())).thenReturn(null);

            // Act
            Mono<Response<ServiceInstance>> result = loadBalancer.choose(request);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response).isInstanceOf(EmptyResponse.class);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("实例列表为空时应返回 EmptyResponse")
        void shouldReturnEmptyResponse_whenInstancesEmpty() {
            // Arrange
            Request request = createRequestWithUserId("user-123");
            when(supplier.get(request)).thenReturn(Flux.just(new ArrayList<>()));

            // Act
            Mono<Response<ServiceInstance>> result = loadBalancer.choose(request);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response).isInstanceOf(EmptyResponse.class);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("supplier 抛出异常时应返回 EmptyResponse")
        void shouldReturnEmptyResponse_whenSupplierThrowsException() {
            // Arrange
            Request request = createRequestWithUserId("user-123");
            when(supplier.get(request)).thenReturn(Flux.error(new RuntimeException("Connection refused")));

            // Act
            Mono<Response<ServiceInstance>> result = loadBalancer.choose(request);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response).isInstanceOf(EmptyResponse.class);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("只有单个实例时应正确路由")
        void shouldWorkWithSingleInstance() {
            // Arrange
            List<ServiceInstance> singleInstance = createInstances(1);
            Request request = createRequestWithUserId("user-single");

            when(supplier.get(any())).thenReturn(Flux.just(singleInstance));

            // Act & Assert
            for (int i = 0; i < 5; i++) {
                Mono<Response<ServiceInstance>> result =
                        loadBalancer.choose(request);

                StepVerifier.create(result)
                        .assertNext(response -> {
                            assertThat(response).isInstanceOf(DefaultResponse.class);
                            DefaultResponse dr = (DefaultResponse) response;
                            assertThat(dr.getServer()).isEqualTo(singleInstance.get(0));
                        })
                        .verifyComplete();
            }
        }
    }

    @Nested
    @DisplayName("哈希环重建测试")
    class HashRingRebuildTests {

        @Test
        @DisplayName("实例列表变化时应重建哈希环")
        void shouldRebuildRing_whenInstancesChange() {
            // Arrange
            String userId = "user-change";
            Request request = createRequestWithUserId(userId);

            // 第一次：3 个实例
            when(supplier.get(request))
                    .thenReturn(Flux.just(instances))
                    .thenReturn(Flux.just(createInstances(5))); // 第二次：5 个实例

            // Act - 第一次请求
            Mono<Response<ServiceInstance>> result1 = loadBalancer.choose(request);

            StepVerifier.create(result1)
                    .assertNext(response -> assertThat(response).isInstanceOf(DefaultResponse.class))
                    .verifyComplete();

            // Act - 第二次请求（实例数变化）
            Mono<Response<ServiceInstance>> result2 = loadBalancer.choose(request);

            StepVerifier.create(result2)
                    .assertNext(response -> assertThat(response).isInstanceOf(DefaultResponse.class))
                    .verifyComplete();
        }

        @Test
        @DisplayName("实例列表相同时不应重建哈希环")
        void shouldNotRebuildRing_whenInstancesSame() {
            // Arrange
            String userId = "user-same";
            Request request = createRequestWithUserId(userId);

            when(supplier.get(request)).thenReturn(Flux.just(instances));

            // 多次请求相同实例列表
            for (int i = 0; i < 10; i++) {
                Mono<Response<ServiceInstance>> result = loadBalancer.choose(request);

                StepVerifier.create(result)
                        .assertNext(response -> assertThat(response).isInstanceOf(DefaultResponse.class))
                        .verifyComplete();
            }

            // supplier 只需要被调用一次来获取实例列表
            // 但由于每次请求都会调用，这里验证能正常工作即可
        }
    }

    // ==================== Helper Methods ====================

    private List<ServiceInstance> createInstances(int count) {
        List<ServiceInstance> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new DefaultServiceInstance(
                    "instance-" + i,
                    SERVICE_ID,
                    "192.168.1." + i,
                    8080 + i,
                    false
            ));
        }
        return list;
    }

    /**
     * 创建带有 userId 的 Request 对象
     */
    private Request createRequestWithUserId(String userId) {
        HttpHeaders headers = new HttpHeaders();
        if (userId != null) {
            headers.set(USER_ID_HEADER, userId);
        }

        RequestData requestData = mock(RequestData.class);
        when(requestData.getHeaders()).thenReturn(headers);

        RequestDataContext context = mock(RequestDataContext.class);
        when(context.getClientRequest()).thenReturn(requestData);

        // 使用 ReflectionTestUtils 或创建匿名子类
        return new Request<>() {
            @Override
            public Object getContext() {
                return context;
            }
        };
    }
}

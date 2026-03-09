package com.ticketbooking.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.ticketbooking.common.exception.FeignResultException;
import com.ticketbooking.common.result.Result;
import feign.FeignException;
import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Feign 响应解码器
 * <p>
 * 自动解包 Result<T> 为 T，如果 Result 不成功则抛出 FeignResultException
 * <p>
 * 使用方式：在 FeignClientConfig 中配置此 Decoder
 * <pre>
 * {@code
 * @Bean
 * public Decoder feignDecoder(ObjectMapper objectMapper) {
 *     return new FeignResultDecoder(objectMapper);
 * }
 * }
 * </pre>
 */
@Slf4j
public class FeignResultDecoder implements Decoder {

    private final ObjectMapper objectMapper;

    public FeignResultDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, DecodeException, FeignException {
        // 读取响应体
        String bodyStr = Util.toString(response.body().asReader(Util.UTF_8));

        // 如果返回类型是 void，直接返回 null
        if (type == Void.class || type == void.class) {
            return null;
        }

        // 如果返回类型已经是 Result，直接反序列化
        if (isResultType(type)) {
            return objectMapper.readValue(bodyStr, getJavaType(type));
        }

        // 构造 Result<T> 的类型
        JavaType resultType = TypeFactory.defaultInstance()
                .constructParametricType(Result.class, getJavaType(type));

        // 反序列化为 Result<T>
        Result<?> result = objectMapper.readValue(bodyStr, resultType);

        if (result == null) {
            throw new DecodeException(response.status(), "响应结果为空", response.request());
        }

        // 如果成功，返回 data
        if (result.isSuccess()) {
            return result.getData();
        }

        // 如果失败，抛出 FeignResultException
        log.warn("Feign调用失败: code={}, message={}", result.getCode(), result.getMessage());
        throw new FeignResultException(extractServiceName(response), result.getCode(), result.getMessage());
    }

    /**
     * 判断类型是否为 Result
     */
    private boolean isResultType(Type type) {
        if (type instanceof ParameterizedType parameterizedType) {
            Type rawType = parameterizedType.getRawType();
            return rawType == Result.class;
        }
        return type == Result.class;
    }

    /**
     * 获取 Java 类型
     */
    private JavaType getJavaType(Type type) {
        return objectMapper.constructType(type);
    }

    /**
     * 从响应中提取服务名称
     */
    private String extractServiceName(Response response) {
        String url = response.request().url();
        // 从 URL 中提取服务名称，例如 http://ticket-service/internal/grades/1 -> ticket-service
        try {
            String host = new java.net.URL(url).getHost();
            return host;
        } catch (Exception e) {
            return "unknown-service";
        }
    }
}

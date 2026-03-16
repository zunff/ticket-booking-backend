package com.ticketbooking.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.exception.SystemException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Feign Error Decoder - 将HTTP错误响应转换为自定义 Exception
 */
@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        // 4xx 业务异常，不触发熔断
        if (response.status() >= 400 & response.status() < 500) {
            String errorMessage = extractErrorMessage(response);
            log.warn("Feign call failed: business error methodKey={}, status={}, message={}", methodKey, response.status(), errorMessage);
            return new BusinessException(response.status(), errorMessage);
        }
        // 5xx 服务异常，触发熔断
        if (response.status() >= 500) {
            String errorMessage = extractErrorMessage(response);
            log.warn("Feign call failed: system error methodKey={}, status={}, message={}", methodKey, response.status(), errorMessage);
            return new SystemException(response.status(), errorMessage);
        }
        return defaultDecoder.decode(methodKey, response);
    }

    private String extractErrorMessage(Response response) {
        try {
            if (response.body() != null) {
                InputStream is = response.body().asInputStream();
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(is, HashMap.class);
                Object message = result.get("message");
                return message != null ? message.toString() : "远程服务调用失败";
            }
        } catch (IOException e) {
            log.warn("Failed to extract error message from response", e);
        }
        return "远程服务调用失败";
    }
}

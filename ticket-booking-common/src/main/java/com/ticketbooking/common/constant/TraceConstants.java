package com.ticketbooking.common.constant;

/**
 * 链路追踪常量
 */
public final class TraceConstants {

    private TraceConstants() {
    }

    /**
     * TraceId 请求头名称
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * MDC 中的 TraceId 键名
     */
    public static final String TRACE_ID_KEY = "traceId";
}

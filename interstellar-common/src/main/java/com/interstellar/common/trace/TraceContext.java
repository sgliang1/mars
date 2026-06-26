package com.interstellar.common.trace;

import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Component;

/**
 * OpenTelemetry 链路追踪上下文工具
 * 提供当前 traceId / spanId 的快捷访问
 */
@Component
public class TraceContext {

    /**
     * 获取当前 span 的 traceId（32 位 hex）
     * @return traceId，若无活跃 span 则返回 "unknown"
     */
    public String currentTraceId() {
        Span span = Span.current();
        if (span.getSpanContext().isValid()) {
            return span.getSpanContext().getTraceId();
        }
        return "unknown";
    }

    /**
     * 获取当前 span 的 spanId（16 位 hex）
     * @return spanId，若无活跃 span 则返回 "unknown"
     */
    public String currentSpanId() {
        Span span = Span.current();
        if (span.getSpanContext().isValid()) {
            return span.getSpanContext().getSpanId();
        }
        return "unknown";
    }
}

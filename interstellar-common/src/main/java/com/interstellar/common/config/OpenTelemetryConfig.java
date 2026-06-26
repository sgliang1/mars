package com.interstellar.common.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ServiceAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry 手动配置
 * 替代 opentelemetry-spring-boot-starter（该 starter 与 Spring Boot 3.4 存在 incubator 类冲突）
 * 提供 SDK 初始化 + OTLP HTTP 导出 + TraceContext 工具所需的 Tracer
 */
@Slf4j
@Configuration
public class OpenTelemetryConfig {

    @Value("${spring.application.name:interstellar-service}")
    private String serviceName;

    @Bean
    @ConditionalOnProperty(name = "otel.exporter.otlp.endpoint")
    public OpenTelemetry openTelemetry(
            @Value("${otel.exporter.otlp.endpoint}") String otlpEndpoint) {
        Resource resource = Resource.getDefault()
                .merge(Resource.create(
                        io.opentelemetry.api.common.Attributes.of(
                                ServiceAttributes.SERVICE_NAME, serviceName)));

        // 使用 OTLP HTTP exporter（端口 4318），而非 gRPC（端口 4317）
        SpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(otlpEndpoint + "/v1/traces")
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .setSampler(Sampler.parentBased(Sampler.alwaysOn()))
                .build();

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        log.info("OpenTelemetry SDK 初始化完成: service={}, endpoint={}", serviceName, otlpEndpoint);
        return sdk;
    }

    @Bean
    public Tracer otelTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("interstellar-instrumentation");
    }
}

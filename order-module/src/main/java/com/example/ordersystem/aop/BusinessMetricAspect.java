package com.example.ordersystem.aop;

import com.example.ordersystem.annotation.BusinessMetric;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
public class BusinessMetricAspect {

    @Autowired
    private MeterRegistry meterRegistry;

    @Around("@annotation(com.example.ordersystem.annotation.BusinessMetric)")
    public Object collectMetrics(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        BusinessMetric annotation = signature.getMethod().getAnnotation(BusinessMetric.class);
        String operation = annotation.operation();
        boolean recordDetail = annotation.recordFailureDetail();

        // 记录开始时间
        long start = System.currentTimeMillis();
        Object result = null;
        boolean success = true;
        Throwable exception = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            success = false;
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - start;
            // 指标上报
            Counter successCounter = Counter.builder("business.operation.total")
                    .tags(Tags.of("operation", operation, "result", "success"))
                    .register(meterRegistry);
            Counter failureCounter = Counter.builder("business.operation.total")
                    .tags(Tags.of("operation", operation, "result", "failure"))
                    .register(meterRegistry);

            if (success) {
                successCounter.increment();
            } else {
                failureCounter.increment();
                // 记录失败上下文（结构化日志 + MDC）
                if (recordDetail) {
                    try (MDC.MDCCloseable op = MDC.putCloseable("business_operation", operation);
                         MDC.MDCCloseable err = MDC.putCloseable("business_error_message", exception.getMessage());
                         MDC.MDCCloseable args = MDC.putCloseable("business_args", argsToString(joinPoint.getArgs()))) {
                        log.error("业务操作失败: operation={}", operation, exception);
                    }
                }
            }

            // 记录耗时（可选）
            meterRegistry.timer("business.operation.duration",
                            Tags.of("operation", operation, "result", success ? "success" : "failure"))
                    .record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
            System.out.println("已执行BusinessMetric任务");
        }
    }

    private String argsToString(Object[] args) {
        // 简易实现，生产环境建议使用 JSON 序列化
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            sb.append(arg).append(",");
        }
        return sb.toString();
    }
}
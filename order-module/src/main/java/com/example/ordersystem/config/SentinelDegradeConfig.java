package com.example.ordersystem.config;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 熔断降级规则配置
 * 针对支付检查接口（资源名：payCheck）设置慢调用比例和异常比例熔断
 */
@Configuration
public class SentinelDegradeConfig {

    // 注意：Feign 调用的资源名默认是：接口全限定名#方法名(参数类型)
    // 例如：com.example.ordersystem.client.PayClient#checkPayment(Long,Long,String,Integer)
    // 但为了简化，建议使用 @SentinelResource 显式指定资源名，或直接配置该名称。
    // 如果使用 fallback，Sentinel 会自动埋点，资源名为上述格式。
    // 我们可以通过配置规则时使用该名称，或者统一用 @SentinelResource 包装。

    // 推荐方式：在调用处使用 @SentinelResource 包装，这样资源名可控。
    // 因此我们不需要修改 PayClient，而是在 OrderStockTransactionService 中包装调用。

    @PostConstruct
    public void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        // 1. 慢调用比例熔断规则
        DegradeRule slowRule = new DegradeRule("payCheck")
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(200)                      // 慢调用阈值（RT），单位毫秒，超过200ms算慢调用
                .setTimeWindow(3)                  // 熔断时长，单位秒，熔断后3秒进入半开状态
                .setStatIntervalMs(2000)            // 统计时长，单位毫秒，2秒
                .setMinRequestAmount(2)             // 最小请求数，达到2次后才计算比例
                .setSlowRatioThreshold(0.5);        // 慢调用比例阈值，50%
        rules.add(slowRule);

        // 2. 异常比例熔断规则
        DegradeRule exceptionRule = new DegradeRule("payCheck")
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.5)                      // 异常比例阈值，50%
                .setTimeWindow(3)                  // 熔断时长，3秒
                .setStatIntervalMs(2000)            // 统计时长，2秒
                .setMinRequestAmount(2);             // 最小请求数
        rules.add(exceptionRule);

        DegradeRuleManager.loadRules(rules);
        System.out.println("Sentinel熔断降级规则已加载: " + rules);
    }
}
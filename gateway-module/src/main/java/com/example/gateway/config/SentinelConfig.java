package com.example.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;

@Configuration
public class SentinelConfig {

    @PostConstruct
    public void initGatewayRules() {
        initCustomizedApis();
        initGatewayFlowRules();
    }

    private void initCustomizedApis() {
        Set<ApiDefinition> definitions = new HashSet<>();

        // API 1: 订单主页限流组
        ApiDefinition api1 = new ApiDefinition("orders_api")
                .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                    add(new ApiPathPredicateItem()
                            .setPattern("/orders")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT));
                }});
        // API 2: 下单接口限流组
        ApiDefinition api2 = new ApiDefinition("create_order_api")
                .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                    add(new ApiPathPredicateItem()
                            .setPattern("/order/create")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT));
                }});
        // API 3: 支付检查接口限流组
        ApiDefinition api3 = new ApiDefinition("pay_check_api")
                .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                    add(new ApiPathPredicateItem()
                            .setPattern("/api/pay/check")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT));
                }});

        definitions.add(api1);
        definitions.add(api2);
        definitions.add(api3);
        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
    }

    private void initGatewayFlowRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        // 规则1: /orders 每3秒最多3次
        GatewayFlowRule rule1 = new GatewayFlowRule("orders_api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setGrade(1)  // 1: QPS
                .setCount(3)
                .setIntervalSec(3);
        // 规则2: /order/create 每5秒最多5次
        GatewayFlowRule rule2 = new GatewayFlowRule("create_order_api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setGrade(1)
                .setCount(5)
                .setIntervalSec(5);
        // 规则3: /api/pay/check 每5秒最多5次
        GatewayFlowRule rule3 = new GatewayFlowRule("pay_check_api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setGrade(1)
                .setCount(5)
                .setIntervalSec(5);

        rules.add(rule1);
        rules.add(rule2);
        rules.add(rule3);
        GatewayRuleManager.loadRules(rules);
    }

    @Bean
    public SentinelGatewayFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }
}
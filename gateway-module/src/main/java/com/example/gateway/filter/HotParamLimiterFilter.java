package com.example.gateway.filter;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Component
public class HotParamLimiterFilter implements GlobalFilter, Ordered {

    @PostConstruct
    public void initHotParamRules() {
        // 针对资源 "createOrder" 设置热点参数限流规则
        ParamFlowRule rule = new ParamFlowRule("createOrder")
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setParamIdx(0)               // 第一个参数（userId）
                .setCount(30)                  // 3秒内允许30次
                .setDurationInSec(3)          // 时间窗口3秒
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);

        // 为特定 userId=1 设置单独阈值（可选）
        // 注意：classType 需要与实际参数类型一致，这里使用 long
        // 但为了简化，不设置例外项，统一阈值 3 次/3秒
        // 如果需要针对 userId=1 更严格，可以添加以下代码：
        /*
        ParamFlowItem item = new ParamFlowItem()
                .setObject("1")
                .setClassType(long.class.getName())
                .setCount(2);   // 2次/3秒
        rule.setParamFlowItemList(Collections.singletonList(item));
        */

        ParamFlowRuleManager.loadRules(Collections.singletonList(rule));
        System.out.println("Hot param rules loaded: " + ParamFlowRuleManager.getRules());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!"/order/create".equals(path)) {
            return chain.filter(exchange);
        }

        String userIdHeader = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userIdHeader == null) {
            return chain.filter(exchange);
        }

        long userId;
        try {
            userId = Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            return chain.filter(exchange);
        }

        // 注意：传递的参数类型必须与规则中 classType 一致，这里统一用 long
        Entry entry = null;
        try {
            entry = SphU.entry("createOrder", EntryType.IN, 1, userId);
            return chain.filter(exchange);
        } catch (BlockException ex) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            System.out.println("Trigger rate limit for userId=" + userId);
            return exchange.getResponse().setComplete();
        } finally {
            if (entry != null) {
                entry.exit(1, userId);
            }
        }
    }

    @Override
    public int getOrder() {
        return -99; // 在 GatewayJwtAuthenticationFilter（-100）之后执行
    }
}
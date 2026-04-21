package com.example.ordersystem.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.example.ordersystem.client.PayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PayCheckService {

    @Autowired
    private PayClient payClient;

    /**
     * 调用支付检查，带 Sentinel 保护（先走snetinel保护逻辑，再走feignCLient逻辑）
     */
    //支付检查慢调用或抛出异常：如果慢调用比例超过阈值，Sentinel 会熔断，后续请求直接进入 payCheckFallbackBySentinel，不再调用 Feign。
    //Sentinel熔断降级策略（这里和Feign fallback不一致！）
    //慢调用比例、异常比例超过阈值（业务层面），防止系统雪崩，快速失败并降级
    @SentinelResource(value = "payCheck", fallback = "payCheckFallbackBySentinel")
    public boolean doPayCheck(Long orderId, Long userId, String productName, Integer quantity) {
        return payClient.checkPayment(orderId, userId, productName, quantity);
    }

    /**
     * 由Sentinel主导的降级方法（参数与原始方法一致，可增加 Throwable 参数）
     */
    public boolean payCheckFallbackBySentinel(Long orderId, Long userId, String productName, Integer quantity, Throwable ex) {
        log.warn("Sentinel>>>触发支付检查降级，原因: {}", ex != null ? ex.getMessage() : "unknown");
        // 降级逻辑：允许支付
        return true;
    }
}
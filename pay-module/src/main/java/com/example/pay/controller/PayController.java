package com.example.pay.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@RestController
@RequestMapping("/api/pay")
public class PayController {

    /**
     * 支付检查接口
     * @param orderId 订单ID
     * @param userId 用户ID
     * @param productName 商品名称
     * @param quantity 商品数量
     * @return true=允许支付，false=不允许
     */
    @PostMapping("/check")
    public boolean checkPayment(@RequestParam("orderId") Long orderId,
                                @RequestParam("userId") Long userId,
                                @RequestParam("productName") String productName,
                                @RequestParam("quantity") Integer quantity) {
        // 模拟随机延时（1-300ms），用于触发慢调用熔断
        try {
            int delay = ThreadLocalRandom.current().nextInt(1, 3000);
            Thread.sleep(delay);
            log.info("支付检查请求模拟随机延时:{}ms", delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("收到支付检查请求: orderId={}, userId={}, productName={}, quantity={}",
                orderId, userId, productName, quantity);
        // 简单规则：商品数量 <= 20 允许支付
        boolean allowed = quantity != null && quantity <= 20;
        log.info("支付检查结果: {}", allowed);
        return allowed;
    }
}
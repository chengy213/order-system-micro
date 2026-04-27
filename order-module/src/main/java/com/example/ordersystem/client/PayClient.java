package com.example.ordersystem.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "pay-module", url = "${pay.service.url:http://localhost:8083}")
public interface PayClient {

    /**
     * 调用支付检查服务
     * @param orderId 订单ID（可选）
     * @param userId 用户ID
     * @param productName 商品名称
     * @param quantity 商品数量
     * @return 支付检查结果（true=允许支付）
     */
    @PostMapping("/api/pay/check")
    boolean checkPayment(@RequestParam("orderId") Long orderId,
                         @RequestParam("userId") Long userId,
                         @RequestParam("productName") String productName,
                         @RequestParam("quantity") Integer quantity);
}
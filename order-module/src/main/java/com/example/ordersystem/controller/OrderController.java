package com.example.ordersystem.controller;

import com.example.ordersystem.entity.Order;
import com.example.ordersystem.messaging.rocketmq.StockTransactionMessagePublisher;
import com.example.ordersystem.security.SecurityUser;
import com.example.ordersystem.service.OrderService;
import com.example.ordersystem.service.StockService;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Web 端订单页面控制器（Thymeleaf 渲染）
 * 使用 @AuthenticationPrincipal 获取当前登录用户信息
 */
@Controller
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private StockService stockService;

    @Autowired
    private StockTransactionMessagePublisher stockTxPublisher;

    /**
     * 订单主页：展示订单列表
     */
    @GetMapping("/orders")
    public String ordersPage(@AuthenticationPrincipal SecurityUser user, Model model) {
        Long userId = user.getUserId();
        List<Order> orders = orderService.getOrdersByUserId(userId);
        int availableStock = stockService.getAvailableStock(userId);
        model.addAttribute("orders", orders);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("availableStock", availableStock);
        return "orders";
    }

    /**
     * 创建新订单（下单）
     */
    @PostMapping("/order/create")
    public String createOrder(@RequestParam String productName,
                              @RequestParam Integer quantity,
                              @AuthenticationPrincipal SecurityUser user,
                              RedirectAttributes redirectAttributes) {
        Long userId = user.getUserId();
        try {
            TransactionSendResult result = stockTxPublisher.sendCreateOrderTransaction(userId, productName, quantity);
            if (result.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE) {
                redirectAttributes.addFlashAttribute("success", "下单成功");
            } else {
                redirectAttributes.addFlashAttribute("error", "下单失败：" + (result.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE ? "支付检查未通过" : "未知错误"));
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "下单失败：" + e.getMessage());
        }
        return "redirect:/orders";
    }

    /**
     * 取消订单
     */
    @PostMapping("/order/cancel/{orderId}")
    public String cancelOrder(@PathVariable Long orderId,
                              @AuthenticationPrincipal SecurityUser user,
                              RedirectAttributes redirectAttributes) {
        Long userId = user.getUserId();
        try {
            // 不再直接取消订单，改为发送事务消息
            stockTxPublisher.sendCancelOrderTransaction(orderId, userId);
            redirectAttributes.addFlashAttribute("success", "订单已取消");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "取消失败：" + e.getMessage());
        }
        return "redirect:/orders";
    }
}
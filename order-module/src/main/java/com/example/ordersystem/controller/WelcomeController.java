package com.example.ordersystem.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 欢迎页控制器，处理根路径和 /welcome
 * 如果用户已登录则跳转到订单主页，否则显示欢迎页
 */
@Controller
public class WelcomeController {

    @GetMapping({"/", "/welcome"})
    public String home() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 判断是否已认证且不是匿名用户
        if (authentication != null && authentication.isAuthenticated() &&
                !(authentication.getPrincipal() instanceof String && authentication.getPrincipal().equals("anonymousUser"))) {
            return "redirect:/orders";
        }
        return "welcome";
    }
}
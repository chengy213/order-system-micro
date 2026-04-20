package com.example.ordersystem.security;

import com.example.ordersystem.service.RefreshTokenService;
import com.example.ordersystem.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Component
public class CustomLogoutHandler implements LogoutHandler {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${jwt.cookie-name}")
    private String cookieName;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // 1. 从 Cookie 中获取 Refresh Token 和 JWT Token
        String refreshTokenValue = null;
        String jwtToken = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("REFRESH_TOKEN".equals(cookie.getName())) {
                    refreshTokenValue = cookie.getValue();
                }
                if (cookieName.equals(cookie.getName())) {
                    jwtToken = cookie.getValue();
                }
            }
        }

        // 2. 删除 Refresh Token
        if (refreshTokenValue != null) {
            refreshTokenService.deleteRefreshToken(refreshTokenValue);
        }

        // 3. 从 JWT 中解析用户 ID（如果存在）
        Long userId = null;
        if (jwtToken != null) {
            try {
                userId = jwtUtil.extractUserId(jwtToken);
            } catch (Exception e) {
                // 解析失败则忽略
            }
        }

        // 4. 清除用户相关缓存
        if (userId != null) {
            userService.clearUserCache(userId);
        }
    }
}
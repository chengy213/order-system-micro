package com.example.ordersystem.security;

import com.example.ordersystem.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Component
public class CustomLogoutHandler implements LogoutHandler {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // 从 Cookie 中获取 REFRESH_TOKEN 的值
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("REFRESH_TOKEN".equals(cookie.getName())) {
                    String refreshTokenValue = cookie.getValue();
                    if (refreshTokenValue != null && !refreshTokenValue.isEmpty()) {
                        // 从 Redis 中删除该 Refresh Token
                        refreshTokenService.deleteRefreshToken(refreshTokenValue);
                    }
                    break;
                }
            }
        }
    }
}
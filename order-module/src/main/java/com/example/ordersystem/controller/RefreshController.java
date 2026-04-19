package com.example.ordersystem.controller;

import com.example.ordersystem.config.ClientProperties;
import com.example.ordersystem.entity.RefreshToken;
import com.example.ordersystem.security.JwtUtil;
import com.example.ordersystem.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RefreshController {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ClientProperties clientProperties;

    @Value("${jwt.cookie-name}")
    private String cookieName;

    @Value("${jwt.expiration}")
    private Long accessTokenExpiration;

    /**
     * 刷新 Access Token（支持 Web 用户和 API 客户端）
     * Web 用户：Refresh Token 从 Cookie 中读取（自动刷新，无感知）
     * API 客户端：Refresh Token 从请求头 "Refresh-Token" 中读取
     */
    @PostMapping("/refresh_token")
    public ResponseEntity<?> refreshAccessToken(@RequestHeader(value = "Refresh-Token", required = false) String refreshTokenValue,
                                                HttpServletResponse response) {
        // 如果请求头中没有，尝试从 Cookie 中获取（用于 Web 端自动刷新）
        if (refreshTokenValue == null) {
            // 这里可以添加从 Cookie 获取的逻辑（但 Web 端刷新已在过滤器中实现，此处作为备用）
            return ResponseEntity.status(401).body(Map.of("error", "Refresh token missing"));
        }

        RefreshToken refreshToken = refreshTokenService.validateRefreshToken(refreshTokenValue);
        if (refreshToken == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired refresh token"));
        }

        String type = refreshToken.getType();
        String newAccessToken;

        if ("user".equals(type)) {
            // 用户刷新：生成新 Access Token，并更新 Cookie
            Long userId = refreshToken.getUserId();
            String username = refreshToken.getUsername();
            newAccessToken = jwtUtil.generateToken(username, userId);
            // 更新 Cookie（可选，如果 Web 端也使用此接口）
            Cookie cookie = new Cookie(cookieName, newAccessToken);
            cookie.setHttpOnly(true);
            cookie.setSecure(false);   // 生产环境设为 true
            cookie.setPath("/");
            cookie.setMaxAge((int) (accessTokenExpiration / 1000));
            response.addCookie(cookie);
        } else if ("client".equals(type)) {
            // 客户端刷新：根据 clientId 获取 scopes
            String clientId = refreshToken.getClientId();
            ClientProperties.Client client = clientProperties.getClients().get(clientId);
            if (client == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Client not found"));
            }
            List<String> scopes = client.getScopes();
            newAccessToken = jwtUtil.generateTokenForClient(clientId, scopes);
            // 客户端刷新不更新 Cookie（API 客户端不使用 Cookie），只返回 JSON
        } else {
            return ResponseEntity.status(400).body(Map.of("error", "Invalid token type"));
        }

        return ResponseEntity.ok(Map.of(
                "access_token", newAccessToken,
                "token_type", "Bearer",
                "expires_in", accessTokenExpiration / 1000
        ));
    }
}
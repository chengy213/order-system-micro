package com.example.ordersystem.service;

import com.example.ordersystem.entity.RefreshToken;
import com.example.ordersystem.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class RefreshTokenService {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * 生成一个新的 Refresh Token（随机字符串）
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 为用户创建 Refresh Token 并存储
     */
    public RefreshToken createUserRefreshToken(Long userId, String username) {
        String tokenValue = generateRefreshToken();
        RefreshToken refreshToken = new RefreshToken(
                tokenValue, username, "user", userId, null, Instant.now()
        );
        // 一个用户只保留一个有效的 Refresh Token，先删除旧的
        refreshTokenRepository.deleteByUsername(username);
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * 为客户端创建 Refresh Token 并存储
     */
    public RefreshToken createClientRefreshToken(String clientId) {
        String tokenValue = generateRefreshToken();
        RefreshToken refreshToken = new RefreshToken(
                tokenValue, clientId, "client", null, clientId, Instant.now()
        );
        refreshTokenRepository.deleteByUsername(clientId);
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * 验证 Refresh Token 并返回实体（如果有效）
     */
    public RefreshToken validateRefreshToken(String tokenValue) {
        return refreshTokenRepository.findById(tokenValue).orElse(null);
    }

    /**
     * 删除 Refresh Token（登出时调用）
     */
    public void deleteRefreshToken(String tokenValue) {
        refreshTokenRepository.deleteById(tokenValue);
    }
}
package com.example.ordersystem.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT 工具类：生成、解析、验证 JWT
 * 支持两种类型：
 * - 用户 token：包含 userId, username, type=user
 * - 客户端 token：包含 clientId, scopes, type=client
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;          // 签名密钥

    @Value("${jwt.expiration}")
    private Long expiration;        // 有效期（毫秒）

    /**
     * 获取签名密钥（HMAC-SHA256）
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 为用户生成 JWT
     * @param username 用户名
     * @param userId   用户ID
     */
    public String generateToken(String username, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "user");
        claims.put("userId", userId);
        return createToken(claims, username);
    }

    /**
     * 为客户端生成 JWT（包含 scopes 权限）
     * @param clientId 客户端ID
     * @param scopes   权限范围列表
     */
    public String generateTokenForClient(String clientId, List<String> scopes) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "client");
        claims.put("clientId", clientId);
        claims.put("scopes", scopes);
        return createToken(claims, clientId);
    }

    /**
     * 创建 JWT 的核心方法
     * @param claims  自定义声明
     * @param subject 主题（用户名或客户端ID）
     */
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 从 JWT 中提取用户名（subject）
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * 从 JWT 中提取用户ID（仅对用户 token 有效）
     */
    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    /**
     * 从 JWT 中提取 token 类型（user/client）
     */
    public String extractType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    /**
     * 从 JWT 中提取客户端ID（仅对客户端 token 有效）
     */
    public String extractClientId(String token) {
        return extractClaim(token, claims -> claims.get("clientId", String.class));
    }

    /**
     * 从 JWT 中提取权限范围列表（仅对客户端 token 有效）
     */
    public List<String> extractScopes(String token) {
        return extractClaim(token, claims -> claims.get("scopes", List.class));
    }

    /**
     * 提取 JWT 的过期时间
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * 提取指定声明
     */
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * 解析 JWT 获取所有声明
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 判断 JWT 是否已过期
     */
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * 验证 JWT 是否有效（仅验证签名和过期时间）
     */
    public Boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            System.out.println("Token expired: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.out.println("Token validation error: " + e.getMessage());
            return false;
        }
    }
}
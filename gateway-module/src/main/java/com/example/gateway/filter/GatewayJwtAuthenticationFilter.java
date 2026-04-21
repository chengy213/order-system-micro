package com.example.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Gateway 模块的 JWT 认证过滤器
 * 负责验证 JWT，并提取 userId 添加到请求头 X-User-Id 中，供下游服务和 Sentinel 限流使用
 */
@Component
public class GatewayJwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String secret;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // 公开路径模式（支持通配符）
    private static final String[] PUBLIC_PATHS = {
            "/", "/welcome", "/login", "/logout", "/error",
            "/css/**", "/js/**", "/admin/**"
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 公开路径直接放行
        for (String publicPath : PUBLIC_PATHS) {
            if (pathMatcher.match(publicPath, path)) {
                return chain.filter(exchange);
            }
        }

        // 对 /api/pay/** 进行 JWT 鉴权（如果需要）
        if (pathMatcher.match("/api/pay/**", path)) {
            String token = extractToken(exchange.getRequest());
            if (token == null || !validateToken(token)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
        }

        // 对于所有需要认证的路径（包括 /order/create），尝试提取 userId 并添加到请求头
        String token = extractToken(exchange.getRequest());
        if (token != null && validateToken(token)) {
            try {
                Long userId = extractUserIdFromToken(token);
                if (userId != null) {
                    // 将 userId 放入请求头，供 Sentinel 参数限流使用
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header("X-User-Id", String.valueOf(userId))
                            .build();
                    exchange = exchange.mutate().request(mutatedRequest).build();
                }
            } catch (Exception e) {
                // 解析失败，忽略，继续执行
            }
        }

        return chain.filter(exchange);
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        HttpCookie cookie = request.getCookies().getFirst("JWT_TOKEN");
        return cookie != null ? cookie.getValue() : null;
    }

    private boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Long extractUserIdFromToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Object userIdObj = claims.get("userId");
            if (userIdObj instanceof Number) {
                return ((Number) userIdObj).longValue();
            } else if (userIdObj instanceof String) {
                return Long.parseLong((String) userIdObj);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int getOrder() {
        return -100; // 高优先级，确保在其他过滤器之前执行
    }
}
package com.example.ordersystem.security;

import com.example.ordersystem.entity.RefreshToken;
import com.example.ordersystem.service.RefreshTokenService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器，拦截每个请求，从 Cookie 或 Authorization Header 中提取 JWT 并验证
 * 验证成功后根据 token 类型（user/client）构建对应的 Principal 并设置到 SecurityContext
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${jwt.cookie-name}")
    private String cookieName;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String jwt = null;

        // 1. 优先从 Authorization Header 获取（适用于 API 调用）
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        }

        // 2. 如果没有 Header，再从 Cookie 获取（适用于 Web 页面）
        if (jwt == null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookieName.equals(cookie.getName())) {
                        jwt = cookie.getValue();
                        break;
                    }
                }
            }
        }

        // 3. 验证 JWT 并设置认证信息
        if (jwt != null) {
            try {
                if (jwtUtil.validateToken(jwt)) {
                    // 有效，正常设置认证
                    setAuthentication(jwt, request);
                } else {
                    // 无效（可能是过期），尝试刷新
                    throw new ExpiredJwtException(null, null, "Token expired");
                }
            } catch (ExpiredJwtException e) {
                // version4-重点改动：当 Access Token 过期时，尝试从 Cookie 中获取 Refresh Token，若有效则签发新 Access Token，并更新 Cookie，然后继续处理请求。
                // Access Token 过期，尝试使用 Refresh Token 刷新
                boolean refreshed = tryRefreshToken(request, response);
                if (!refreshed) {
                    // 刷新失败，继续匿名处理（后续过滤器会返回401）
                    SecurityContextHolder.clearContext();
                }
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                System.out.println("JWT processing error: "+ e.getMessage());
            }
        }
        // 无论 jwt 是否为 null，都必须放行，否则请求将被阻塞，永远不会到达 DispatcherServlet，浏览器等待后超时或显示空白页面。
        filterChain.doFilter(request, response);
    }

    private void setAuthentication(String jwt, HttpServletRequest request) {
        String type = jwtUtil.extractType(jwt);
        Object principal = null;

        if ("user".equals(type)) {
            // 用户 token：提取 userId 和 username
            String username = jwtUtil.extractUsername(jwt);
            Long userId = jwtUtil.extractUserId(jwt);
            principal = new SecurityUser(userId, username);
        } else if ("client".equals(type)) {
            // 客户端 token：提取 clientId 和 scopes
            String clientId = jwtUtil.extractClientId(jwt);
            List<String> scopes = jwtUtil.extractScopes(jwt);
            principal = new ClientPrincipal(clientId, scopes);
        }

        if (principal != null) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null,
                            principal instanceof org.springframework.security.core.userdetails.UserDetails ?
                                    ((org.springframework.security.core.userdetails.UserDetails) principal).getAuthorities() : List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    /**
     * 尝试刷新 Access Token（仅对 Web 端 Cookie 有效）
     */
    private boolean tryRefreshToken(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        String refreshTokenValue = null;
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("REFRESH_TOKEN".equals(cookie.getName())) {
                    refreshTokenValue = cookie.getValue();
                    break;
                }
            }
        }
        if (refreshTokenValue == null) {
            return false;
        }

        RefreshToken refreshToken = refreshTokenService.validateRefreshToken(refreshTokenValue);
        if (refreshToken == null) {
            return false;
        }

        // 根据类型生成新的 Access Token
        String newAccessToken;
        if ("user".equals(refreshToken.getType())) {
            newAccessToken = jwtUtil.generateToken(refreshToken.getUsername(), refreshToken.getUserId());
            // 更新 Access Token Cookie
            Cookie newCookie = new Cookie(cookieName, newAccessToken);
            newCookie.setHttpOnly(true);
            newCookie.setSecure(false);
            newCookie.setPath("/");
            newCookie.setMaxAge((int) (expiration / 1000));
            response.addCookie(newCookie);

            // 重新设置认证信息
            SecurityUser securityUser = new SecurityUser(refreshToken.getUserId(), refreshToken.getUsername());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return true;
        } else {
            // 客户端 Refresh Token 不在此自动刷新，应由客户端调用 /api/refresh 接口
            return false;
        }
    }
}
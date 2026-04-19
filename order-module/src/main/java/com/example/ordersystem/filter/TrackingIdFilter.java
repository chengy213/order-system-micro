package com.example.ordersystem.filter;

import com.example.ordersystem.util.GeneSnowflake;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TrackingIdFilter extends OncePerRequestFilter {

    private static final String TRACKING_HEADER = "X-Tracking-Id";
    private static final String TRACKING_COOKIE = "X-Tracking-Id";  // Cookie 名称
    private static final String MDC_KEY = "trackingId";

    @Autowired
    private GeneSnowflake geneSnowflake;

    @Value("${tracking.cookie.max-age:86400}")  // 默认 1 天
    private int cookieMaxAge;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String trackingId = null;

            // 1. 优先从请求头获取（用于服务间调用）
            trackingId = request.getHeader(TRACKING_HEADER);
            if (trackingId != null && !trackingId.isEmpty()) {
                // 若请求头携带，则同步更新 Cookie（保证后续请求也能继续使用）
                addTrackingCookie(response, trackingId);
            } else {
                // 2. 从 Cookie 中获取
                Cookie[] cookies = request.getCookies();
                if (cookies != null) {
                    for (Cookie cookie : cookies) {
                        if (TRACKING_COOKIE.equals(cookie.getName())) {
                            trackingId = cookie.getValue();
                            break;
                        }
                    }
                }
            }

            // 3. 若仍然没有，则生成新的
            if (trackingId == null || trackingId.isEmpty()) {
                //使用 Hutool 的雪花算法生成 ID
                trackingId = String.valueOf(geneSnowflake.nextId());
                addTrackingCookie(response, trackingId);
            }

            // 4. 放入 MDC 供日志使用
            MDC.put(MDC_KEY, trackingId);

            // 5. 添加响应头（方便外部系统或调试）
            response.setHeader(TRACKING_HEADER, trackingId);

            // 6. 继续执行后续过滤器
            filterChain.doFilter(request, response);
        } finally {
            // 7. 清除 MDC，防止内存泄漏
            MDC.clear();
        }
    }

    private void addTrackingCookie(HttpServletResponse response, String trackingId) {
        Cookie cookie = new Cookie(TRACKING_COOKIE, trackingId);
        cookie.setHttpOnly(false);        // 允许前端 JS 读取（可选，若需要前端手动获取则设 false）
        cookie.setSecure(false);          // 开发环境可设为 false，生产环境应设为 true（需 HTTPS）
        cookie.setPath("/");              // 整个应用有效
        cookie.setMaxAge(cookieMaxAge);   // 有效期（秒）
        // 可选：设置 SameSite 属性（若需要跨站传递）
        // cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
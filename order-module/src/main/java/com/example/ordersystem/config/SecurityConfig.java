package com.example.ordersystem.config;

import com.example.ordersystem.filter.TrackingIdFilter;
import com.example.ordersystem.security.CustomLogoutHandler;
import com.example.ordersystem.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置类
 * - 禁用 CSRF（因为使用 JWT 无状态认证）
 * - 配置公开路径和需要认证的路径
 * - 使用 JWT 过滤器代替 Session
 * - 启用方法级安全注解 @PreAuthorize
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)  // 启用 @PreAuthorize 注解
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private TrackingIdFilter trackingIdFilter;

    @Autowired
    private CustomLogoutHandler customLogoutHandler;

    /**
     * 密码编码器，使用 BCrypt 单向哈希
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 安全过滤器链配置
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())   // 禁用 CSRF（无状态 JWT 不需要）
                .authorizeHttpRequests(auth -> auth
                        // 公开路径：欢迎页、登录页、静态资源、获取 token 的 API
                        .requestMatchers("/", "/welcome", "/login", "/api/token", "/api/refresh_token", "/admin/**", "/css/**", "/js/**", "/error", "/druid").permitAll()
                        // 其余所有请求都需要认证
                        .anyRequest().authenticated()
                )
                // 使用无状态会话（不创建 HttpSession）
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(trackingIdFilter, UsernamePasswordAuthenticationFilter.class)  // 最先执行
                // 添加 JWT 过滤器，在 UsernamePasswordAuthenticationFilter 之前执行
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 登出配置（清除 Cookie）
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/welcome")
                        .permitAll()
                        .addLogoutHandler(customLogoutHandler)           // 添加自定义处理器
                        .deleteCookies("JWT_TOKEN", "REFRESH_TOKEN")     // 清除两个 Cookie
                );

        return http.build();
    }
}
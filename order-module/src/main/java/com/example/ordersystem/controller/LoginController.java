package com.example.ordersystem.controller;

import com.example.ordersystem.entity.RefreshToken;
import com.example.ordersystem.security.JwtUtil;
import com.example.ordersystem.service.RefreshTokenService;
import com.example.ordersystem.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 处理用户登录页面和登录逻辑
 * 登录成功后生成用户 JWT，并存入 HttpOnly Cookie
 */
@Controller
public class LoginController {

    @Autowired
    private UserService userService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${jwt.cookie-name}")
    private String cookieName;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    /**
     * 显示登录页面
     */
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    /**
     * 处理登录表单提交
     * 验证用户名密码，成功后生成 JWT 并设置 Cookie
     */
    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpServletResponse response,
                          Model model) {
        Long userId = userService.authenticateAndGetUserId(username, password);
        if (userId != null) {
            // 生成用户 JWT，包含 userId 和 username
            String token = jwtUtil.generateToken(username, userId);

            // 将 JWT 存入 HttpOnly Cookie，以便浏览器自动携带
            Cookie cookie = new Cookie(cookieName, token);
            cookie.setHttpOnly(true);       // 防止 XSS 攻击，不允许js读取该属性!
            cookie.setSecure(false);        // 生产环境应设为 true (HTTPS)
            //Domain=.zoom.us: 指定Cookie可被所有 zoom.us 的子域名访问。
            //cookie.setDomain(".zoom.us"); // 允许子域名共享
            cookie.setPath("/");            // 整个应用路径有效
            cookie.setMaxAge((int) (expiration / 1000));
            response.addCookie(cookie);

            // 在 doLogin 方法中，用户认证成功后，添加以下代码：

            // 生成 Refresh Token 并存入 Redis
            RefreshToken refreshToken = refreshTokenService.createUserRefreshToken(userId, username);
            // 将 Refresh Token 也存入 HttpOnly Cookie（可选），或者仅在响应体中返回给前端
            // 为了 Web 端无感知刷新，我们将 Refresh Token 存入另一个 Cookie
            Cookie refreshCookie = new Cookie("REFRESH_TOKEN", refreshToken.getId());
            refreshCookie.setHttpOnly(true);
            refreshCookie.setSecure(false);
            refreshCookie.setPath("/");
            refreshCookie.setMaxAge(refreshExpiration.intValue());
            response.addCookie(refreshCookie);

            return "redirect:/orders";
        } else {
            model.addAttribute("error", "用户名或密码错误");
            return "login";
        }
    }
}
禁用 CSRF（Cross-Site Request Forgery，跨站请求伪造）意味着你的应用不再防御一种经典的网络攻击：
攻击者诱导已登录用户访问恶意网站，【该网站利用用户的浏览器自动携带目标网站的 Cookie（包括你的 JWT Token）】，
向你的后端发送伪造的请求，执行非用户本意的操作。

一、CSRF 攻击的具体示例（以你的订单系统为例）
假设你的订单系统部署在 https://your-orders.com，用户登录后浏览器中存有 JWT_TOKEN Cookie（HttpOnly，但浏览器会自动携带到任何同源请求）。
攻击者搭建一个恶意网站 https://evil.com，并在其页面中嵌入以下代码：
<img src="https://your-orders.com/order/create?productName=垃圾商品&quantity=100" style="display:none">
或者通过自动提交表单：
<form id="hack" action="https://your-orders.com/order/create" method="POST">
    <input name="productName" value="垃圾商品">
    <input name="quantity" value="100">
</form>
<script>document.getElementById('hack').submit();</script>

攻击步骤：
- 用户正常登录你的订单系统，浏览器中保存了 JWT_TOKEN Cookie。
- 用户在同一浏览器中访问攻击者的恶意网站 evil.com。
- 恶意网站自动发起请求到 https://your-orders.com/order/create（POST 或 GET，取决于你的接口）。
- 由于浏览器自动携带 JWT_TOKEN Cookie，你的后端会认为这是已认证用户的合法请求，从而执行下单操作。
- 用户完全无感知，攻击者成功让用户下单了垃圾商品（甚至可能扣费或占用库存）。

业务影响：
- 用户的账户被恶意下单、取消订单、修改信息等。
- 如果存在转账、修改密码等敏感操作，后果更严重。
- 即使你的 API 只允许 POST 请求，攻击者同样可以利用表单自动提交或 AJAX（受同源策略限制，但表单提交不受同源限制）来发送 POST 请求。

二、为什么禁用 CSRF 会允许这种攻击？
- CSRF 防护的核心机制：在请求中携带一个攻击者无法获取的额外 token（通常是服务端生成并存储在会话中或 Cookie 中的随机值，且由前端通过表单隐藏域或请求头提交）。
- 当你禁用 CSRF 后，后端不再验证这个 token，仅依赖 Cookie 中的 JWT 来认证。由于浏览器会自动携带 Cookie，攻击者构造的请求也能通过认证。
注意：即使你的 JWT 存储在 HttpOnly Cookie 中，也无法防止 CSRF，因为浏览器会自动携带 Cookie 到同源请求，无论该请求是否由用户本人触发。

三、什么时候禁用 CSRF 可以接受？
- 你的应用完全无状态，且所有写操作都要求 Authorization 头 而不是 Cookie（即 JWT 放在 Authorization: Bearer 头中，而不是 Cookie）。此时浏览器不会自动携带该头，因此 CSRF 无法攻击。
- 你的应用仅对内网或受信任环境开放，用户不会访问恶意网站。
- 你的应用所有敏感操作都需要额外验证（如短信验证码、图形验证码）。
- 你使用了 SameSite=Strict 或 Lax 属性设置 Cookie，限制了跨站请求携带 Cookie。

四、针对你的项目的具体建议
- 如果你坚持使用 Cookie 存储 JWT，并且希望保持简单，可以设置 Cookie 的 SameSite=Strict 属性（现代浏览器支持），这能有效防御 CSRF，因为跨站请求不会携带该 Cookie。
- 或者将 JWT 移到 Authorization 头，前端手动从 localStorage 读取并附加（但面临 XSS 风险）。
- 最平衡的方案：启用 CSRF 防护，配合 Thymeleaf 的 th:action 自动生成隐藏域，对业务影响极小（只需确保所有表单使用 th:action）。

[具体实现]

下面为您提供 启用 CSRF 防护 的完整实现，同时保持 JWT 无状态和 API 无感。
关键点：使用 CookieCsrfTokenRepository（不依赖 Session），对 Web 页面路径启用 CSRF，
对 /api/** 路径禁用 CSRF（因为外部 API 不使用 Cookie 认证，且 JWT 通过 Authorization 头传递，不受 CSRF 威胁）。

一、改动概览
文件	                                改动内容
SecurityConfig.java	            启用 CSRF，配置 CookieCsrfTokenRepository，并忽略 /api/** 路径
orders.html	                    将退出链接改为 POST 表单，以便携带 CSRF token
LoginController.java	        无需修改（表单已使用 th:action，自动添加 _csrf）
ApiController.java	            无需修改（已忽略 CSRF）
application.properties	        可添加 Cookie 安全属性配置（可选）

二、详细代码改动
1. 修改 SecurityConfig.java

package com.example.ordersystem.config;

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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 配置 CSRF：使用 Cookie 存储 token，不依赖 Session
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        // 设置 Cookie 属性（可选，增强安全性）
        csrfTokenRepository.setCookieCustomizer((cookie) -> {
            cookie.httpOnly(false);   // 允许前端 JS 读取（用于 Ajax），但我们的表单使用隐藏域，其实不需要，保持默认 false 即可
            cookie.secure(true);      // 生产环境必须 HTTPS
            cookie.sameSite("Lax");   // 防止 CSRF 的同时不影响正常跳转
        });

        // 处理 CSRF token 的请求属性（用于 Thymeleaf 自动生成隐藏域）
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName("_csrf");

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(requestHandler)
                // 对 /api/** 路径禁用 CSRF（外部 API 不使用 Cookie 认证）
                .ignoringRequestMatchers("/api/**")
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/welcome", "/login", "/api/token", "/api/refresh_token", "/css/**", "/js/**", "/error").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/welcome")
                .permitAll()
                .deleteCookies("JWT_TOKEN", "REFRESH_TOKEN")
            );

        return http.build();
    }
}

说明：
- CookieCsrfTokenRepository.withHttpOnlyFalse()：生成的 Cookie 名称为 XSRF-TOKEN，前端可读（但我们的 Thymeleaf 表单使用隐藏域，不依赖读取 Cookie）。
- .ignoringRequestMatchers("/api/**")：所有以 /api 开头的路径跳过 CSRF 检查，保证外部 API 调用不受影响。
- CsrfTokenRequestAttributeHandler：将 CSRF token 暴露为请求属性 _csrf，以便 Thymeleaf 自动生成隐藏域。

三、验证 CSRF 防护生效
- 启动应用，访问 http://localhost:8080/login。
- 打开浏览器开发者工具 → 查看页面源代码，登录表单中应自动包含一个隐藏域：
<input type="hidden" name="_csrf" value="xxxx-xxxx-xxxx" />
- 登录后进入订单页面，点击“退出登录”按钮（POST 表单），应正常登出并重定向到欢迎页。
- 尝试直接通过浏览器地址栏访问 /logout（GET 请求），会返回 403 或 404，因为登出只接受 POST。
- 尝试通过 API 调用 /api/users/1/orders（使用 JWT token），应正常工作，不受 CSRF 影响。

四、安全性与兼容性说明
- Web 页面：所有 POST 表单自动携带 CSRF token，有效防御跨站请求伪造。
- API 接口：/api/** 路径禁用 CSRF，因为外部 API 调用使用 Authorization: Bearer 头，不依赖 Cookie，不存在 CSRF 风险。同时保持了无状态特性。
- 无 Session：使用 CookieCsrfTokenRepository，服务端不存储 CSRF token，符合无状态架构。
- 登出：必须使用 POST 表单，符合 CSRF 要求。

五、常见问题
Q: 为什么登录表单不需要手动添加 _csrf 隐藏域？
A: 因为 th:action 会在渲染时自动从请求属性中获取 CSRF token 并生成隐藏域。

Q: 我的前端是 AJAX 请求怎么办？
A: 对于 AJAX，需要从 Cookie 中读取 XSRF-TOKEN，并设置请求头 X-XSRF-TOKEN。Spring Security 默认支持该方式。但由于您的项目使用 Thymeleaf 表单，无需处理。

Q: 禁用 /api/** 的 CSRF 安全吗？
A: 是的。外部 API 调用通过 Authorization 头传递 JWT，浏览器无法自动携带该头，因此无法发起 CSRF 攻击。即使攻击者构造表单，由于缺少 JWT，请求会被拒绝。

Q:你对CSRF的改动支持中将httpOnly设置为false，但这样不会引发新的XSS脚本注入攻击的问题吗？两者怎么取得一个平衡？
A: 我们需要先理清：这里配置的Cookie是XSRF-TOKEN（CSRF token），而不是JWT token。JWT token的Cookie仍然是HttpOnly=true。
所以风险仅限于CSRF token被XSS窃取。而CSRF token本身不应该被用于直接认证，只是用于防CSRF。
如果XSS窃取了CSRF token，攻击者可以利用它发起CSRF请求，但前提是攻击者也需要知道目标URL和参数等。
实际上，CSRF token被窃取后，攻击者可以构造一个包含正确CSRF token的请求，从而绕过CSRF防护。
然而，XSS本身已经可以执行任意脚本，攻击者完全可以直接调用API（利用用户的Cookie/JWT）执行任何操作，根本不需要CSRF token。
所以，一旦存在XSS，CSRF token的泄露不是额外风险，因为攻击者已经可以完全控制页面。
因此，设置httpOnly=false并不会引入新的实质性风险，而只是为了方便前端通过JavaScript读取CSRF token（比如SPA应用）。
对于传统表单应用，其实可以保持httpOnly=true，因为CSRF token通过隐藏域注入，不需要前端JS读取。
但Spring Security的CookieCsrfTokenRepository.withHttpOnlyFalse()默认就是false，因为前端需要读取Cookie值来设置请求头（例如在AJAX中）。
如果你的应用不使用AJAX而是纯表单提交，你可以自定义配置为httpOnly(true)，这样更安全。

三、平衡建议：如何同时防御 XSS 和 CSRF
- 首要目标：防御 XSS（因为 XSS 可以摧毁所有其他防护）
    - 严格的输出编码（使用 Thymeleaf 默认转义）。
    - 配置 CSP (Content-Security-Policy) 头，限制脚本来源。
    - 使用 HttpOnly 保护 JWT Cookie（已经做了）。
- CSRF 防护：
    - 对于非跨域场景：使用 httpOnly=true 的 CSRF Cookie + 隐藏域提交，最安全。
    - 对于跨域场景（如子域名依赖主域名登录）：
        - 如果必须使用 Cookie 传递 CSRF Token，并且前端需要读取，可以设置 httpOnly=false，但必须配合严格的 XSS 防御。
        - 更推荐的做法：将 CSRF Token 放在响应头中，由前端 JS 读取并添加到请求头（但同样面临 XSS 风险）。
        - 或者完全放弃 Cookie 形式的 JWT，改用 Authorization 头 + localStorage，并手动管理 CSRF Token（复杂度高）。

- 针对你的跨域子系统的具体方案：
    - 保持 JWT Cookie 为 HttpOnly=true、SameSite=None、Secure、Domain=.zoom.us。
    - 启用 CSRF 防护，使用 CookieCsrfTokenRepository，并设置 httpOnly=false（因为前端需要读取）。
    - 额外加强 XSS 防护：
        - CSP 策略：default-src 'self'; script-src 'self' 'unsafe-inline'? 尽量不使用 unsafe-inline。
        - 所有动态内容严格编码。
        - 定期扫描依赖库漏洞。

- 对 /api/** 路径禁用 CSRF（外部 API 不受影响）。
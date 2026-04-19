两种方案的核心区别：Spring Security + Spring Session Redis vs JWT (HttpOnly Cookie)

维度	        Spring Security + Spring Session Redis	                    JWT (HttpOnly Cookie)
会话存储	    服务器端存储（Redis 集中存储），客户端仅存 JSESSIONID	        客户端存储（Cookie），服务端无状态
认证信息	    SecurityContext 保存在 HttpSession 中，                  SecurityUser 从 JWT 解析后动态构建，SecurityContext 每次请求重新创建
            由 Spring Session 同步到 Redis
会话 ID	    JSESSIONID 是随机生成的 UUID，指向 Redis 中的会话数据	    JWT_TOKEN 本身包含用户信息，无需额外查询
扩展性	    需共享 Redis，支持水平扩展	                                完全无状态，无需共享存储，天然支持水平扩展
性能	        每次请求需从 Redis 读取会话（可能带来网络开销）	            每次请求仅需本地验证 JWT 签名（计算开销极低）
安全性	    会话可主动失效；但需防范 CSRF（通常启用 CSRF 防护）	        JWT 一旦签发无法主动失效（除非维护黑名单）；HttpOnly Cookie 防止 XSS 窃取，但需防范 CSRF（可禁用或使用 SameSite）
注销	        session.invalidate() 删除 Redis 中的会话，立即失效	        客户端删除 Cookie，但 JWT 在有效期内仍可能被使用（除非服务器维护黑名单）
适用场景	    传统 Web 应用、需要精细管理会话（如强制踢人、会话列表）、         前后端分离、移动端、微服务、对扩展性要求极高的场景
            服务端渲染

一、核心原理对比
1. Spring Security + Spring Session Redis
登录：用户提交用户名密码 → Spring Security 认证成功 → 创建 HttpSession → SecurityContext 存入该 session → Spring Session 拦截 session 操作，将数据写入 Redis → 响应头 Set-Cookie: JSESSIONID=xxx; HttpOnly。

后续请求：浏览器携带 JSESSIONID Cookie → Spring Session 过滤器根据该 ID 从 Redis 加载 session → SecurityContextPersistenceFilter 将 SecurityContext 恢复并存入 SecurityContextHolder。

注销：调用 session.invalidate() → Spring Session 删除 Redis 中的 key，同时清除客户端 Cookie。

2. JWT (HttpOnly Cookie)
登录：用户提交用户名密码 → 服务端验证后生成 JWT（包含 userId、username、过期时间等） → 将 JWT 放入 HttpOnly Cookie 返回客户端。

后续请求：浏览器自动携带 JWT_TOKEN Cookie → JwtAuthenticationFilter 解析 JWT，验证签名和有效期 → 从 JWT 中提取用户信息构建 SecurityUser → 创建 UsernamePasswordAuthenticationToken 并存入 SecurityContextHolder（该认证对象仅存在于当前请求，请求结束后丢弃）。

注销：客户端删除 Cookie（服务端可设置 deleteCookies），但 JWT 本身在有效期内仍有效（除非服务端额外维护黑名单）。

二、认证流程对比（以访问 /orders 为例）
方案一：Spring Security + Spring Session Redis
// 1. 浏览器请求 /orders，携带 Cookie: JSESSIONID=abc123
// 2. SessionRepositoryFilter 拦截，根据 abc123 从 Redis 加载会话
Session session = redisSessionRepository.findById("abc123");
// 3. SecurityContextPersistenceFilter 从 session 中获取 SecurityContext
SecurityContext context = session.getAttribute("SPRING_SECURITY_CONTEXT");
SecurityContextHolder.setContext(context);  // 此时 context 中有 Authentication 对象
// 4. 授权决策通过，进入 OrderController
@GetMapping("/orders")
public String ordersPage(HttpSession session) {   // 可以直接注入 HttpSession
    // 该 session 实际上是由 Redis 代理的，可存取任意数据
    session.setAttribute("someData", "value");  // 自动同步到 Redis
    // 通过 SecurityContextHolder 获取用户
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return "orders";
}

方案二：JWT (HttpOnly Cookie)
// 1. 浏览器请求 /orders，携带 Cookie: JWT_TOKEN=eyJhbGciOiJIUzI1NiIs...
// 2. JwtAuthenticationFilter 拦截，从 Cookie 读取 JWT
String jwt = extractJwtFromCookie(request);
if (jwtUtil.validateToken(jwt)) {
    String username = jwtUtil.extractUsername(jwt);
    Long userId = jwtUtil.extractUserId(jwt);
    SecurityUser user = new SecurityUser(userId, username);
    Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    SecurityContextHolder.setContext(new SecurityContextImpl(auth));
}
// 3. 授权决策通过，进入 OrderController
@GetMapping("/orders")
public String ordersPage(@AuthenticationPrincipal SecurityUser user) {
    // 直接从参数获取，无需访问 HttpSession
    // 如果需要临时存储数据，必须使用其他方式（如 Redis、数据库）
    return "orders";
}

三、会话存储与管理对比
操作	                Spring Session Redis	                            JWT
存储位置	            服务器 Redis（集中存储）	                    客户端 Cookie（分散存储）
会话数据大小	        可任意大（如购物车列表）	                    受 Cookie 大小限制（通常 4KB），不宜存大量数据
数据生命周期	        由服务器控制（可手动失效、设置过期时间）	        由 JWT 自身的 exp 控制，服务器无法主动失效
跨域支持	            需配置 Cookie 的 Domain 和 SameSite，较复杂	可轻松支持跨域（若使用 Authorization 头）

四、水平扩展能力对比
Spring Session Redis：所有实例连接同一 Redis 集群，会话共享，扩展只需增加实例，无需额外配置。缺点是每次请求都需访问 Redis，可能成为瓶颈。

JWT：完全无状态，任意实例均可独立验证 JWT，扩展性极佳。缺点是 JWT 无法主动撤销，且刷新 token 机制增加复杂度。

五、安全性对比
攻击类型	            Spring Session Redis	                            JWT (HttpOnly Cookie)
XSS	                HttpOnly Cookie 防止 JS 读取，有效防护	                同左，有效防护
CSRF	            需启用 CSRF 防护（默认开启），需在表单中嵌入 token	    若 Cookie 为 SameSite=Strict 或禁用 CSRF（前后端分离常用），可减轻风险；但若使用 Cookie 自动携带，仍需 CSRF 防护
会话劫持	            会话 ID 泄露后，攻击者可冒充，                          JWT 泄露后，攻击者在有效期内可冒充，且服务端无法主动失效
                    但可通过 IP 绑定、UA 校验增强
会话固定	            Spring Security 默认启用 migrateSession()，          JWT 不存在会话固定问题，因为每次请求重新验证 token
                    登录后变更 session ID

六、示例场景演示
场景：用户登录后，查看订单列表
Spring Session Redis：
1.用户登录，服务器生成 JSESSIONID=abc，存入 Redis 会话（包含 SecurityContext）。
2.浏览器收到 Set-Cookie: JSESSIONID=abc; HttpOnly。
3.用户访问 /orders，浏览器携带 Cookie。
4.服务器从 Redis 加载会话，恢复 SecurityContext，控制器直接使用。
5.若用户登出，session.invalidate() 删除 Redis 中的会话，后续请求无法加载。

JWT：
1.用户登录，服务器生成 JWT（有效期 30 分钟），放入 HttpOnly Cookie。
2.浏览器收到 Set-Cookie: JWT_TOKEN=eyJ...; HttpOnly。
3.用户访问 /orders，浏览器携带 Cookie。
4.服务器验证 JWT 签名，从 token 中提取 userId，构建临时认证对象，控制器获取用户信息。
5.若用户登出，服务器删除 Cookie，但 JWT 本身在有效期内仍然有效（如果攻击者提前获取 token，仍可使用）。如需更强安全，可维护 token 黑名单。

七、如何选择？
7.1 选择 Spring Session Redis 当：
    - 需要服务端主动管理会话（如踢出用户、实时查看在线人数）。
    - 应用为传统服务端渲染，且对性能要求不极端。
    - 团队熟悉 HttpSession 编程模型，希望保持简单。
    - 需要存储大量会话数据（如购物车、用户临时状态）。

7.2 选择 JWT 当：
    - 应用为前后端分离，需要 API 被移动端、小程序等调用。
    - 追求极致的水平扩展能力，避免 Redis 成为瓶颈。
    - 希望无状态，便于在容器化环境中自动扩缩容。
    - 能接受 token 无法主动失效的缺点，并实现刷新 token 机制。

混合使用：也可同时使用两种机制，例如为 Web 页面使用 Session，为 API 使用 JWT，但需谨慎处理安全策略。


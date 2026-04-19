JWT 版本下的用户请求认证流程（以下单为例）

用户提交下单表单
↓
浏览器自动携带 Cookie（包含 JWT_TOKEN 和可能遗留的 JSESSIONID）
↓
请求到达 Spring Security 过滤器链
↓
JwtAuthenticationFilter 拦截请求（在 UsernamePasswordAuthenticationFilter 之前）
↓
从 Cookie 中获取名为 JWT_TOKEN 的值
↓
调用 JwtUtil.validateToken(token) 验证 JWT 的签名和过期时间
↓
验证成功：从 JWT 中提取 userId 和 username
↓
创建 SecurityUser 对象（包含 userId、username、默认角色）
↓
创建 UsernamePasswordAuthenticationToken 并存入 SecurityContextHolder
↓
验证失败：不设置认证，SecurityContextHolder 保持匿名状态
↓
请求继续执行后续过滤器（如 AuthorizationFilter）
↓
AuthorizationFilter 检查当前请求路径 /order/create 是否需要认证（配置为 authenticated）
↓
检查 SecurityContextHolder 中是否存在认证信息
↓
存在认证：授予访问权限，请求进入 OrderController
↓
不存在认证：返回 403 或重定向到登录页
↓
OrderController 通过 @AuthenticationPrincipal SecurityUser user 获取当前用户
↓
调用 OrderService.createOrder(userId, productName, quantity) 执行业务逻辑
↓
返回响应（如重定向到 /orders 或错误消息）
↓
浏览器收到响应，完成下单操作

关键步骤解释
过滤器链起点：所有请求先经过 JwtAuthenticationFilter（在 UsernamePasswordAuthenticationFilter 之前）。

JWT 提取与验证：过滤器从 Cookie 中读取 JWT_TOKEN，使用 JwtUtil.validateToken 检查签名和有效期。

设置 SecurityContext：验证通过后，根据 JWT 中的 userId 和 username 构建 SecurityUser，并创建 UsernamePasswordAuthenticationToken 存入 SecurityContextHolder。

授权决策：后续 AuthorizationFilter 检查当前 SecurityContext 中是否有认证信息，以及当前请求路径的权限要求（/order/create 需要 authenticated）。

控制器处理：OrderController 通过 @AuthenticationPrincipal SecurityUser user 获取当前登录用户信息，调用 OrderService 完成下单。

响应返回：整个过程中无需查询数据库获取用户信息（除非业务需要），实现了真正的无状态认证。
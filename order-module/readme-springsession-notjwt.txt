一、httpsession会话交由spring session托管。
Pom.xml文件
<!-- Spring Session Data Redis (将 HttpSession 存入 Redis) -->
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-data-redis</artifactId>
</dependency>

# Spring Session 使用 Redis 存储
spring.session.store-type=redis
spring.session.redis.namespace=order:session

1.以上就实现了Spring Session 接管 HttpSession
当你添加了 spring-session-data-redis 依赖并配置 spring.session.store-type=redis 后，Spring 会用一个 SessionRepositoryFilter 替换原生的 HttpSession 实现。任何对 request.getSession() 的操作，实际上都由 RedisIndexedSessionRepository 完成，数据存储在 Redis 中。redis中存储的内容：
127.0.0.1:6379> keys *
1) "orders:user:1" //这里1是userId，实际上userId会很长
2) "order:session:sessions:fe726c6d-6885-48a5-9e2c-9d72e0bba60b" //这里后面是sessionId

2. 会话固定保护在分布式环境下的工作方式
• request.getSession(false) 会从当前请求的 Cookie 中解析 session id，然后去 Redis 查找对应的会话（若存在则返回，否则返回 null）。
• 调用 oldSession.invalidate() 会从 Redis 中删除该会话的 key，并清除本地的会话引用。
• request.getSession(true) 会生成一个新的 session id，并在 Redis 中创建新的会话记录，同时通过 Cookie 将新 id 返回给浏览器。

3.负载均衡兼容性
由于所有应用实例都读写同一个 Redis，无论用户请求被分发到哪个节点，都能通过 Cookie 中的 session id 找到正确的会话。因此，你的手动会话固定保护在集群环境下同样有效。

二、用户提交表单后的流程（加了Spring Security集成之后）
当前项目：使用了Spring Security + Spring Session Redis + Thymeleaf。认证基于表单登录，会话基于Redis存储的HttpSession。

  用户提交表单（用户名+明文密码）
          ↓
  UsernamePasswordAuthenticationFilter
           ↓
  AuthenticationManager (ProviderManager)
           ↓
  DaoAuthenticationProvider
           ↓
  调用 CustomUserDetailsService.loadUserByUsername(username)
           ↓
  从数据库获取 User 实体 → 转换为 UserDetails
           ↓
  PasswordEncoder.matches(明文密码, UserDetails.getPassword())
           ↓
  成功 → 创建 Authentication 对象，存入 SecurityContextHolder
           ↓
  [新增]SecurityContextPersistenceFilter 将 SecurityContext 存入 HttpSession
           ↓
  [新增]由于 SessionRepositoryFilter 已包装 HttpSession，该存储操作实际由 RedisIndexedSessionRepository 完成，数据写入 Redis
           ↓
  后续请求中，SessionRepositoryFilter 从 Redis 读取会话，恢复 SecurityContext

三、Cookie 值与 Redis Key（SESSION） 的映射关系

1. 浏览器 Cookie
你看到的名称为 SESSION，值为：ZmU3MjZjNmQtNjg4NS00OGE1LTllMmMtOWQ3MmUwYmJhNjBi
这个字符串实际上是 session id 的 Base64 编码。

Spring Session 与 Redis 的关联机制非常清晰，核心是：Cookie 中存储的是经过 Base64 编码的 session id，而 Redis 中的 key 正是基于这个 session id 构建的。

2. 解码得到原始 session id
将上述字符串进行 Base64 解码：
echo "ZmU3MjZjNmQtNjg4NS00OGE1LTllMmMtOWQ3MmUwYmJhNjBi" | base64 -d

输出结果为：fe726c6d-6885-48a5-9e2c-9d72e0bba60b
这正是你在 Redis 中看到的 key 的末尾部分：
order:session:sessions:fe726c6d-6885-48a5-9e2c-9d72e0bba60b

3. Redis Key 的构成规则
Spring Session 使用 RedisIndexedSessionRepository 存储会话（[见1.httpsession会话交由spring session托管。]），key 的默认模式为：
<namespace>:sessions:<session-id>
• <namespace> 由配置项 spring.session.redis.namespace 指定，你的配置是 order:session。
• sessions 是固定段。
• <session-id> 是原始的 UUID 格式 session id。
因此，完整 key 为：order:session:sessions:fe726c6d-6885-48a5-9e2c-9d72e0bba60b。

四、html页面集成spring security防CSRF提交
<h2>用户登录</h2>
<form method="post" th:action="@{/login}">   <!-- 提交到 Spring Security 的处理地址 -->
    <label>用户名：</label>
    <input type="text" name="username" required autofocus/>
    <label>密码：</label>
    <input type="password" name="password" required/>
    <button type="submit">登录</button>
    <p th:if="${error}" class="error" th:text="${error}"></p>
</form>

其中th:action="@{/login}" 是关键，它会：
• 自动添加当前上下文路径。
• 自动注入 CSRF token 隐藏域。
如果手动写 action="/login"，则不会自动注入 CSRF token，导致 403。

>>>配套的后段代码：
// 获取当前登录用户的 userId，这是其中一种方法，但我们目前可以直接使用@AuthenticationPrincipal 注解
private Long getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof SecurityUser securityUser) {
        return securityUser.getUserId();
    }
    throw new IllegalStateException("当前用户未登录");
}

@GetMapping("/orders")
public String ordersPage(@AuthenticationPrincipal SecurityUser user, Model model) {
    Long userId = user.getUserId();
    List<Order> orders = orderService.getOrdersByUserId(userId);
    model.addAttribute("orders", orders);
    model.addAttribute("username", user.getUsername());
    return "orders";
}
背景请查看[二、用户提交表单后的流程]
@AuthenticationPrincipal是从SecurityContext中获取当前认证用户的方式，
而SecurityContext默认存储在HttpSession中（如果使用session-based存储）。
因此两者并不是互斥的，而是协同工作。@AuthenticationPrincipal只是简化了从SecurityContext中提取principal的操作，
但背后的SecurityContext仍然依赖于HttpSession（除非配置了其他存储策略如JWT等）。
因此，如果应用仍使用基于session的认证，那么HttpSession仍然需要，
但我们可以通过@AuthenticationPrincipal来获取用户详情，而无需直接操作session。

举例说明：比如在OrderController中，我们使用@AuthenticationPrincipal SecurityUser user来获取当前用户ID，
这就是利用@AuthenticationPrincipal。
在后台，Spring Security在登录成功后将Authentication对象存入SecurityContextHolder，
并且通过SecurityContextPersistenceFilter将SecurityContext保存到HttpSession。
所以虽然我们在代码中没有显式使用HttpSession，但框架内部仍然使用了它。
因此，HttpSession仍然是必需的，但我们可以通过@AuthenticationPrincipal避免直接操作session来获取用户信息。
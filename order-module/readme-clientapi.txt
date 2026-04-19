对于“常规用户（通过 Web 界面） + 外部第三方 API 调用”的场景，推荐采用 JWT（JSON Web Token） 作为统一的认证凭证，
并同时支持两种传递方式：HttpOnly Cookie（供浏览器自动携带） 和 Authorization 请求头（供 API 客户端手动携带）。
在 JwtAuthenticationFilter 中同时从 Header 和 Cookie 提取 JWT；为第三方提供获取 JWT 的接口（如 OAuth2 客户端凭证模式）。
这样既能保持 Web 端的无感体验，又能为第三方提供标准、无状态的 API 认证。

一、为什么 JWT 比 Session Redis 更适合此场景？
维度	                    Spring Session Redis	                                JWT
API客户端兼容性	        依赖 Cookie 自动携带，                                支持 Authorization: Bearer <token> 标准头，所有 HTTP 客户端天然支持
                        非浏览器客户端需手动管理 Cookie，体验差
无状态性	                需维护 Redis 会话存储，API 请求每次都要查询 Redis	    完全无状态，无需共享存储，API 可独立水平扩展
跨域支持	                Cookie 跨域复杂（需配置 Domain、SameSite）	            通过请求头传输，轻松支持跨域
第三方接入	            需为每个第三方单独发放 JSESSIONID，无法精细控制权限	    可基于 JWT 中自定义声明（如 clientId、scope）实现细粒度权限控制

二、统一 JWT 认证方案设计(总体架构)

Web 用户：登录后 JWT 存入 HttpOnly Cookie，后续请求自动携带。

外部 API 调用方：通过获取 JWT 的方式（如 OAuth2 客户端凭证模式、API Key 换取 JWT），
在请求头 Authorization: Bearer <token> 中传递 JWT。

Spring Security 过滤器：同时从 Cookie 和 Header 中提取 JWT，验证后构建认证对象，实现双渠道认证。

三、两种传递方式的对比与选择
传递方式	                        适用对象	                    优点	                       缺点
HttpOnly Cookie	            内部 Web 用户（浏览器）	    自动携带，防 XSS	        不支持跨域，需 CSRF 防护
Authorization Header	    外部 API 调用方	            跨域友好，标准规范	        需客户端手动设置请求头
最佳实践：同时支持两种方式，由客户端自行选择。Web 前端无需改动，第三方按 API 文档规范携带 Authorization 头即可。

四、为什么不是 Spring Session Redis？

- Cookie 依赖性：外部 API 调用方通常不是浏览器，无法（也不应该）自动处理 Cookie，
若采用 Session 方案，需要他们手动管理 JSESSIONID，极不友好且易出错。
- 状态存储：每增加一个第三方，都需要在 Redis 中维护其会话，无法做到完全无状态，
且无法为第三方单独控制权限（如限制调用频率、特定接口访问）。
- 水平扩展：虽然 Redis 支持集群，但外部 API 高并发时，Redis 可能成为性能瓶颈；JWT 本地验证无此问题。

五、核心设计思路

5.1 统一JWT 认证：使用 JWT 作为唯一凭证，支持两种传递方式：
    - Web 用户：JWT 存入 HttpOnly Cookie，页面跳转自动携带。
    - 外部API调用方：JWT 通过 Authorization: Bearer <token> 请求头传递。
5.2 JWT 类型区分：在 token 中增加 type 字段，区分“用户令牌”（type=user）和“客户端令牌”（type=client）。
    - 用户令牌包含 userId、username，用于 Web 界面。
    - 客户端令牌包含 clientId，用于第三方 API 调用。
5.3 新增客户端凭证接口：提供 /api/token 端点，允许第三方通过 clientId 和 clientSecret 获取客户端 JWT。
5.4 提供三个 API 端点：
    - GET /api/users/{userId}/orders – 获取指定用户的订单列表
    - POST /api/users/{userId}/orders – 为指定用户下单
    - POST /api/users/{userId}/orders/{orderId}/cancel – 取消指定用户的指定订单
5.5 权限控制：所有 API 端点要求认证，通过 @AuthenticationPrincipal ClientPrincipal 获取客户端信息，
并可根据 clientId 进行日志记录或权限扩展。

六、安全增强建议

- JWT 有效期：API 调用的 token 应设置较短有效期（如 30 分钟），配合刷新 token 机制。
- HTTPS 强制：所有 API 接口必须通过 HTTPS 传输，防止 token 被中间人截获。
- 客户端凭证安全：为第三方颁发 clientId 和 clientSecret，并加密存储。
- 黑名单机制（可选）：若需主动撤销 token，可在 Redis 中维护一个短期黑名单，过滤器校验前检查。
- 权限细化：在 JWT 中增加 scopes 字段，第三方只能访问其授权范围内的 API。

七、测试步骤

- 获取 token：
curl -X POST "http://localhost:8080/api/token?clientId=client1&clientSecret=secret1"

- 使用该 token 调用 API：
GET http://localhost:8080/api/users/1/orders
Authorization: Bearer eyJ...
完整请求命令：
curl -H "Authorization: Bearer eyJ..." http://localhost:8080/api/users/1/orders

- 创建订单：
POST http://localhost:8080/api/users/1/orders
Authorization: Bearer eyJ...
Content-Type: application/json
{
  "productName": "API测试商品",
  "quantity": 2
}
完整请求命令：
curl -X POST -H "Authorization: Bearer eyJ..." -H "Content-Type: application/json" -d '{"productName":"API测试商品-1","quantity":2}' http://localhost:8080/api/users/1/orders

- 取消订单：
POST http://localhost:8080/api/users/1/orders/{orderId}/cancel
Authorization: Bearer eyJ...

完整请求命令：
curl -X POST -H "Authorization: Bearer eyJ..."  http://localhost:8080/api/users/1/orders/16/cancel

我们已在现有 JWT 版本项目基础上，增加了 基于 scope 字段的细粒度权限控制，
允许为不同的第三方客户端授予不同的 API 访问权限（如只读、读写）。

以下是具体的实现步骤和代码改动。

一、整体设计
JWT 内容增强：在客户端 JWT 中增加 scopes 字段，值为该客户端的权限列表（如 ["read","write"]）。

Spring Security 方法级授权：通过 @PreAuthorize 注解对 API 方法进行权限校验，确保只有拥有对应 scope 的客户端才能调用。

客户端配置管理：使用 @ConfigurationProperties 将客户端信息（clientId、secret、scopes）从配置文件加载，方便管理。

二、代码改动详情
1. 添加 ClientProperties 配置类

2. 修改 application.properties，定义客户端配置
client.clients.client1.secret=secret1
client.clients.client1.scopes[0]=read
client.clients.client1.scopes[1]=write
client.clients.client2.secret=secret2
client.clients.client2.scopes[0]=read

3. 修改 JwtUtil，支持在生成客户端 JWT 时添加 scopes 字段
// 新增方法：为客户端生成包含 scopes 的 token
public String generateTokenForClient(String clientId, List<String> scopes) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("type", "client");
    claims.put("clientId", clientId);
    claims.put("scopes", scopes);
    return createToken(claims, clientId);
}

// 新增方法：从 token 中提取 scopes
public List<String> extractScopes(String token) {
    return extractClaim(token, claims -> claims.get("scopes", List.class));
}

4. 修改 ClientPrincipal，将 scopes 转换为 GrantedAuthority 集合
package com.example.ordersystem.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ClientPrincipal implements UserDetails {

    private final String clientId;
    private final Collection<? extends GrantedAuthority> authorities;

    public ClientPrincipal(String clientId, List<String> scopes) {
        this.clientId = clientId;
        // 将 scopes 转换为 GrantedAuthority，如 "SCOPE_read", "SCOPE_write"
        this.authorities = scopes.stream()
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .collect(Collectors.toList());
    }

    public String getClientId() {
        return clientId;
    }

    @Override
    public String getUsername() {
        return clientId;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    // 以下方法返回 true 表示账户可用
    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}

5. 修改 JwtAuthenticationFilter，从 token 中解析 scopes 并构造 ClientPrincipal
在 doFilterInternal 方法中，处理客户端 token 时：
else if ("client".equals(type)) {
    String clientId = jwtUtil.extractClientId(jwt);
    List<String> scopes = jwtUtil.extractScopes(jwt);
    principal = new ClientPrincipal(clientId, scopes);
}

6. 修改 ApiController，在获取 token 时根据配置生成包含 scopes 的 JWT

@RestController
@RequestMapping("/api")
public class ApiController {
...
        ClientProperties.Client client = clientProperties.getClients().get(clientId);
        if (client != null && client.getSecret().equals(clientSecret)) {
            List<String> scopes = client.getScopes();
            String token = jwtUtil.generateTokenForClient(clientId, scopes);
        ...
}

7. 启用方法级安全，添加 @EnableGlobalMethodSecurity 注解
在 SecurityConfig 上添加：
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    // ...
}

8. 在 API 方法上使用 @PreAuthorize 进行权限控制
@GetMapping("/users/{userId}/orders")
@PreAuthorize("hasAuthority('SCOPE_read')")
public ResponseEntity<?> getUserOrders(@PathVariable Long userId,
                                       @AuthenticationPrincipal ClientPrincipal client) {
    // 实现...
}

备注：运行起来如果没有write权限，会报如下错误：
MethodSecurityInterceptor    : Failed to authorize ReflectiveMethodInvocation:
public org.springframework.http.ResponseEntity com.example.ordersystem.controller.ApiController.createOrderForUser(java.lang.Long,java.util.Map,com.example.ordersystem.security.ClientPrincipal);
target is of class [com.example.ordersystem.controller.ApiController] with attributes [[authorize: 'hasAuthority('SCOPE_write')', filter: 'null', filterTarget: 'null']]

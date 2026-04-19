package com.example.ordersystem.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 第三方客户端认证主体，实现 UserDetails 接口
 * 用于 API 调用时的认证，权限从 JWT 中的 scopes 转换而来
 */
public class ClientPrincipal implements UserDetails {

    private final String clientId;                                 // 客户端ID
    private final Collection<? extends GrantedAuthority> authorities;  // 权限集合（如 SCOPE_read, SCOPE_write）

    /**
     * 构造方法
     * @param clientId 客户端ID
     * @param scopes   权限范围列表，如 ["read","write"]
     */
    public ClientPrincipal(String clientId, List<String> scopes) {
        this.clientId = clientId;
        // 将 scopes 转换为 GrantedAuthority，添加 "SCOPE_" 前缀，与 @PreAuthorize 中的表达式匹配
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
        return "";   // 客户端认证不依赖密码，返回空
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
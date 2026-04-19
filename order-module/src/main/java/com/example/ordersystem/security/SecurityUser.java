package com.example.ordersystem.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 内部 Web 用户的认证主体，实现 UserDetails 接口
 * 包含 userId 和 username，权限固定为 ROLE_USER
 */
public class SecurityUser implements UserDetails {

    private final Long userId;                  // 用户ID
    private final String username;              // 用户名
    private final Collection<? extends GrantedAuthority> authorities;  // 权限集合

    public SecurityUser(Long userId, String username) {
        this.userId = userId;
        this.username = username;
        // 简单赋予一个默认角色，实际可根据业务从数据库加载
        this.authorities = List.of(() -> "ROLE_USER");
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return "";   // JWT 认证不需要密码，返回空
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
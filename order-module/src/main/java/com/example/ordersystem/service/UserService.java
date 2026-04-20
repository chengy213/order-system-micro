package com.example.ordersystem.service;

import com.example.ordersystem.entity.User;
import com.example.ordersystem.repository.UserRepository;
import com.example.ordersystem.util.RedisCacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 用户服务，提供认证和用户查询功能
 */
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;   // BCrypt 密码编码器

    @Autowired
    private RedisCacheManager redisCacheManager;

    /**
     * 认证用户并返回用户ID
     * @param username 用户名
     * @param password 明文密码
     * @return 认证成功返回用户ID，否则返回 null
     */
    public Long authenticateAndGetUserId(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (passwordEncoder.matches(password, user.getPassword())) {
                return user.getId();
            }
        }
        return null;
    }

    /**
     * 根据用户名查询用户（主要用于启动时初始化）
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 清除指定用户的所有相关缓存
     * @param userId 用户ID
     */
    public void clearUserCache(Long userId) {
        // 删除订单列表缓存
        String ordersKey = RedisCacheManager.getUserOrdersKey(userId);
        redisCacheManager.delete(ordersKey);
        // 删除库存缓存
        String stockKey = RedisCacheManager.getUserStockKey(userId);
        redisCacheManager.delete(stockKey);
        // 未来如有其他用户相关缓存，可在此扩展
    }
}
package com.example.ordersystem.service;

import com.example.ordersystem.entity.User;
import com.example.ordersystem.repository.UserRepository;
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
}
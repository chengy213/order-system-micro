package com.example.ordersystem.controller;

import com.example.ordersystem.entity.User;
import com.example.ordersystem.repository.UserRepository;
import com.example.ordersystem.service.StockService;
import com.example.ordersystem.util.GeneSnowflake;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private GeneSnowflake geneSnowflake;

    @Autowired
    private StockService stockService;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${snowflake.datacenter-id:1}")
    private long datacenterId;

    @PostMapping("/users")
    @Transactional
    public Map<String, Object> createUser(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String rawPassword = payload.get("password");
        if (username == null || rawPassword == null || username.isBlank() || rawPassword.isBlank()) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("用户名已存在");
        }

        // 根据 datacenterId 确定尾数范围
        int expectedMin, expectedMax;
        if (datacenterId <= 49) {
            expectedMin = 0;
            expectedMax = 49;
        } else {
            expectedMin = 50;
            expectedMax = 99;
        }

        long userId;
        int retry = 0;
        do {
            if (retry++ > 100) {
                throw new RuntimeException("无法生成符合条件的用户ID，请检查雪花算法配置");
            }
            // 生成 ID，基因传 0（不影响尾数规则，因为尾数是 ID % 100）
            userId = geneSnowflake.nextId();
        } while ((userId % 100) < expectedMin || (userId % 100) > expectedMax);

        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setAvailableStock(1000);
        user.setVersion(0);   // 新用户版本号初始为 0

//        userRepository.save(user);
        entityManager.persist(user);
        return Map.of("userId", userId, "username", username);
    }

    @PutMapping("/user/{userId}/stock")
    public String setStock(@PathVariable Long userId, @RequestParam int stock) {
        stockService.setStock(userId, stock);
        return "OK";
    }
}
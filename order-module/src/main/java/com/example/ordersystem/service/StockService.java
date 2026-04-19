package com.example.ordersystem.service;

import com.example.ordersystem.entity.User;
import com.example.ordersystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;   // 改为 Object 类型

    private static final String STOCK_CACHE_PREFIX = "user:stock:";

    // 扣减库存（乐观锁）
    @Transactional
    public boolean decreaseStock(Long userId, int amount) {
        int retry = 3;
        while (retry-- > 0) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getAvailableStock() < amount) return false;
            int updated = userRepository.decreaseStock(userId, amount, user.getVersion());
            if (updated > 0) {
                int newStock = user.getAvailableStock() - amount;
                redisTemplate.opsForValue().set(STOCK_CACHE_PREFIX + userId, newStock);
                return true;
            }
        }
        return false;
    }

    // 增加库存（乐观锁）
    @Transactional
    public boolean increaseStock(Long userId, int amount) {
        int retry = 3;
        while (retry-- > 0) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) return false;
            int updated = userRepository.increaseStock(userId, amount, user.getVersion());
            if (updated > 0) {
                int newStock = user.getAvailableStock() + amount;
                redisTemplate.opsForValue().set(STOCK_CACHE_PREFIX + userId, newStock);
                return true;
            }
        }
        return false;
    }

    // 获取库存（缓存优先）
    public int getAvailableStock(Long userId) {
        String key = STOCK_CACHE_PREFIX + userId;
        Object cachedObj = redisTemplate.opsForValue().get(key);
        if (cachedObj instanceof Integer) {
            return (Integer) cachedObj;
        }
        User user = userRepository.findById(userId).orElse(null);
        int stock = user != null ? user.getAvailableStock() : 0;
        redisTemplate.opsForValue().set(key, stock);
        return stock;
    }

    // 管理员设置库存（同时更新数据库 version 和缓存）
    @Transactional
    public void setStock(Long userId, int newStock) {
        // 使用乐观锁：先查出版本号，再更新（若版本号被他人修改则重试）
        int retry = 3;
        while (retry-- > 0) {
            User user = userRepository.findById(userId).orElseThrow();
            int oldStock = user.getAvailableStock();
            int version = user.getVersion();
            // 直接修改实体字段并保存（JPA 会自动增加版本号）
            user.setAvailableStock(newStock);
            int updated = userRepository.updateStockAndVersion(userId, newStock, version);
            if (updated > 0) {
                redisTemplate.opsForValue().set(STOCK_CACHE_PREFIX + userId, newStock);
                return;
            }
        }
        throw new RuntimeException("更新库存失败，请重试");
    }
}
package com.example.ordersystem.service;

import com.example.ordersystem.entity.User;
import com.example.ordersystem.repository.UserRepository;
import com.example.ordersystem.util.RedisCacheManager;
import com.example.ordersystem.util.RedisKeyConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class StockService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisCacheManager redisCacheManager;

    private static final String STOCK_CACHE_PREFIX = "user:stock:";
    private static final long STOCK_CACHE_TTL_SECONDS = 180; // 3分钟

    // 扣减库存（乐观锁）
//    @Transactional
    public boolean decreaseStock(Long userId, int amount) {
        int retry = 3;
        while (retry-- > 0) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getAvailableStock() < amount) return false;
            int updated = userRepository.decreaseStock(userId, amount, user.getVersion());
            if (updated > 0) {
                // 数据库更新成功，只删除缓存，不主动设置新值（避免脏数据）
                String cacheKey = RedisCacheManager.getUserStockKey(userId);
                redisCacheManager.delete(cacheKey);
                return true;
            }
        }
        return false;
    }

    // 增加库存（乐观锁）
//    @Transactional
    public boolean increaseStock(Long userId, int amount) {
        int retry = 3;
        while (retry-- > 0) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) return false;
            int updated = userRepository.increaseStock(userId, amount, user.getVersion());
            if (updated > 0) {
                // 数据库更新成功，但事务可能回滚，因此不设置新值，而只删除缓存，不主动设置新值（避免脏数据）
                String cacheKey = RedisCacheManager.getUserStockKey(userId);
                redisCacheManager.delete(cacheKey);
                return true;
            }
        }
        return false;
    }

    // 获取库存（缓存优先）
    public int getAvailableStock(Long userId) {
        String cacheKey = RedisCacheManager.getUserStockKey(userId);
        Object cached = redisCacheManager.get(cacheKey);
        if (cached instanceof Integer) {
            return (Integer) cached;
        }
        User user = userRepository.findById(userId).orElse(null);
        int stock = user != null ? user.getAvailableStock() : 0;
        // 设置缓存，带过期时间
        redisCacheManager.set(cacheKey, stock, RedisKeyConstants.USER_STOCK_TTL_SECONDS, TimeUnit.SECONDS);
        return stock;
    }

    // 管理员设置库存（同时更新数据库 version 和缓存）
    @Transactional
    public void setStock(Long userId, int newStock) {
        int retry = 3;
        while (retry-- > 0) {
            User user = userRepository.findById(userId).orElseThrow();
            int version = user.getVersion();
            int updated = userRepository.updateStockAndVersion(userId, newStock, version);
            if (updated > 0) {
                String cacheKey = RedisCacheManager.getUserStockKey(userId);
                redisCacheManager.delete(cacheKey);
                return;
            }
        }
        throw new RuntimeException("更新库存失败，请重试");
    }
}
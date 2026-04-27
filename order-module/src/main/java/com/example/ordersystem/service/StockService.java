package com.example.ordersystem.service;

import com.example.ordersystem.entity.User;
import com.example.ordersystem.repository.UserRepository;
import com.example.ordersystem.util.RedisCacheManager;
import com.example.ordersystem.util.RedisKeyConstants;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class StockService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisCacheManager redisCacheManager;

    @Autowired
    private MeterRegistry meterRegistry;

    private static final String STOCK_CACHE_PREFIX = "user:stock:";
    private static final long STOCK_CACHE_TTL_SECONDS = 180; // 3分钟

    // 扣减库存（乐观锁）
//    @Transactional
    public boolean decreaseStock(Long userId, int amount) {
        int retry = 3;
        while (retry-- > 0) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.warn("用户不存在: userId={}", userId);
                return false;
            }
            // 先检查库存是否真的不足
            if (user.getAvailableStock() < amount) {
                // 真实库存不足，记录库存不足指标
                meterRegistry.counter("business.stock.insufficient",
                        "userId", String.valueOf(userId)).increment();
                log.warn("库存不足: userId={}, required={}, available={}", userId, amount, user.getAvailableStock());
                return false;
            }
            int updated = userRepository.decreaseStock(userId, amount, user.getVersion());
            if (updated > 0) {
                // 成功扣减
                // 数据库更新成功，只删除缓存，不主动设置新值（避免脏数据）
                String cacheKey = RedisCacheManager.getUserStockKey(userId);
                redisCacheManager.delete(cacheKey);
                return true;
            }
            // 乐观锁冲突（version 不匹配），重试
        }
        // 重试多次仍失败，记录乐观锁冲突指标
        meterRegistry.counter("business.stock.optimistic_lock_failure",
                "userId", String.valueOf(userId)).increment();
        log.warn("乐观锁冲突导致扣减失败: userId={}, amount={}", userId, amount);
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
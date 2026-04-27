package com.example.ordersystem.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存管理器，封装常用缓存操作
 */
@Component
public class RedisCacheManager {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 从缓存中获取对象
     * @param key 缓存Key
     * @return 缓存对象，不存在返回null
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 设置缓存（带默认过期时间）
     * @param key 缓存Key
     * @param value 值
     * @param ttl 过期时间
     * @param unit 时间单位
     */
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, ttl, unit);
    }

    /**
     * 删除缓存
     * @param key 缓存Key
     */
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 检查缓存是否存在
     * @param key 缓存Key
     * @return true存在
     */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 获取用户库存缓存Key
     */
    public static String getUserStockKey(Long userId) {
        return RedisKeyConstants.USER_STOCK_PREFIX + userId;
    }

    /**
     * 获取用户订单列表缓存Key
     */
    public static String getUserOrdersKey(Long userId) {
        return RedisKeyConstants.USER_ORDERS_PREFIX + userId;
    }

    /**
     * 根据 key 模式获取最新的一个数值（假设 key 末尾为时间戳，字典序最大即最新）
     */
    public Long getLatestValueByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        String latestKey = keys.stream().max(String::compareTo).orElse(null);
        if (latestKey == null) {
            return null;
        }
        Object value = get(latestKey);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
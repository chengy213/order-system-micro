package com.example.ordersystem.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
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
     * 使用 SCAN 命令替代 KEYS，避免阻塞 Redis 服务。
     * @param pattern key 模式，如 "stats:CREATE_ORDER:15min:*"
     * @return 对应的数值，若没有则返回 null
     */
    public Long getLatestValueByPattern(String pattern) {
        // 记录匹配到的 key 中字典序最大的那个，实际项目中可以改成按天存储，这样非当天的数据都能自动过期，也不需要考虑scan过多数据
        String[] latestKeyHolder = {null};

        // 使用 SCAN 命令迭代匹配的 key
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            // 设置 SCAN 参数：匹配模式，每次返回约 1000 个 key（可调整）
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(1000).build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), StandardCharsets.UTF_8);
                    if (latestKeyHolder[0] == null || key.compareTo(latestKeyHolder[0]) > 0) {
                        latestKeyHolder[0] = key;
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Redis SCAN operation failed", e);
            }
            return null;
        });

        if (latestKeyHolder[0] == null) {
            return null;
        }

        Object value = redisTemplate.opsForValue().get(latestKeyHolder[0]);
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
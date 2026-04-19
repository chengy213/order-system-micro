package com.example.ordersystem.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class IdempotentHelper {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "idempotent:";
    private static final String PROCESSING = "PROCESSING";
    private static final String SUCCESS = "SUCCESS";

    /**
     * 尝试获取消息的处理权（原子操作）
     * @param key 唯一标识（建议包含消息ID + 操作类型）
     * @param ttlSeconds 过期时间（秒），用于防止死锁
     * @return true 表示可以继续处理（当前是第一个处理者），false 表示已处理或正在处理
     */
    public boolean tryProcess(String key, long ttlSeconds) {
        String fullKey = KEY_PREFIX + key;
        // 尝试设置状态为 PROCESSING，仅当 key 不存在时成功
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(fullKey, PROCESSING, ttlSeconds, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(success)) {
            return true;
        }
        // 如果已存在，检查状态
        String status = redisTemplate.opsForValue().get(fullKey);
        return SUCCESS.equals(status); // 如果已经是成功状态，不允许重复处理（返回 false）
        // 如果状态是 PROCESSING，返回 false，表示正在处理中
    }

    /**
     * 标记处理成功
     */
    public void markSuccess(String key) {
        String fullKey = KEY_PREFIX + key;
        redisTemplate.opsForValue().set(fullKey, SUCCESS, getRemainingTtl(fullKey), TimeUnit.SECONDS);
    }

    /**
     * 获取剩余 TTL（保持原有过期时间）
     */
    private long getRemainingTtl(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 3600;
    }

    /**
     * 检查是否已成功处理（简化版）
     */
    public boolean isProcessed(String key) {
        String fullKey = KEY_PREFIX + key;
        return SUCCESS.equals(redisTemplate.opsForValue().get(fullKey));
    }
}
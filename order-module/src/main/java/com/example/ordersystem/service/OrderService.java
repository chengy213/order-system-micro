package com.example.ordersystem.service;

import com.example.ordersystem.entity.Order;
import com.example.ordersystem.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_KEY_PREFIX = "orders:user:";

    // ========== 原有业务（订单查询、缓存）==========
    public List<Order> getOrdersByUserId(Long userId) {
        String cacheKey = CACHE_KEY_PREFIX + userId;
        List<Order> cachedOrders = (List<Order>) redisTemplate.opsForValue().get(cacheKey);
        if (cachedOrders != null) {
            return cachedOrders;
        }
        List<Order> orders = orderRepository.findByUserIdOrderByCreateTimeDesc(userId);
        redisTemplate.opsForValue().set(cacheKey, orders, 1, TimeUnit.HOURS);
        return orders;
    }

    // ========== 原有方法（下单 + 扣库存）已废弃，但保留兼容 ==========
    // 注意：Version 10 中下单和取消订单不再直接调用扣库存/回补，而是发送事务消息
    // 如需兼容旧逻辑，可保留以下方法，但建议不再使用

    // ========== 辅助方法 ==========
    public String generateOrderNo() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public void clearUserOrderCache(Long userId) {
        String cacheKey = CACHE_KEY_PREFIX + userId;
        redisTemplate.delete(cacheKey);
    }
}
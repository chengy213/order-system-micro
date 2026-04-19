package com.example.ordersystem.repository;

import com.example.ordersystem.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 订单数据访问层
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
    /**
     * 根据用户ID查询订单，按创建时间倒序排列
     */
    List<Order> findByUserIdOrderByCreateTimeDesc(Long userId);
}
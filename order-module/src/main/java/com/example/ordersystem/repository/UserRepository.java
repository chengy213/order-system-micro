package com.example.ordersystem.repository;

import com.example.ordersystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * 用户数据访问层
 */
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * 根据用户名查询用户
     */
    Optional<User> findByUsername(String username);

    // 乐观锁扣减库存
    @Modifying
    @Query("UPDATE User u SET u.availableStock = u.availableStock - :amount, u.version = u.version + 1 WHERE u.id = :userId AND u.availableStock >= :amount AND u.version = :version")
    int decreaseStock(Long userId, Integer amount, Integer version);

    // 乐观锁增加库存
    @Modifying
    @Query("UPDATE User u SET u.availableStock = u.availableStock + :amount, u.version = u.version + 1 WHERE u.id = :userId AND u.version = :version")
    int increaseStock(Long userId, Integer amount, Integer version);

    @Modifying
    @Query("UPDATE User u SET u.availableStock = :newStock, u.version = u.version + 1 WHERE u.id = :userId AND u.version = :version")
    int updateStockAndVersion(Long userId, Integer newStock, Integer version);
}
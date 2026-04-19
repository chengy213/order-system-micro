package com.example.ordersystem.repository;

import com.example.ordersystem.entity.RefreshToken;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 自动配置与启用
 * Spring Boot 的 RedisRepositoriesAutoConfiguration 会在 classpath 中存在 spring-data-redis 且配置了 Redis 连接时自动生效。
 * 它会扫描所有标注了 @RedisHash 的实体类，并为继承了 CrudRepository 的接口生成动态代理实现。
 *
 * 相当于隐式执行了 @EnableRedisRepositories（如果没有显式配置的话）。
 * 因此 RefreshTokenRepository 虽然只是一个空接口，但 Spring 会在运行时为其创建代理 bean。
 */
@EnableRedisRepositories //Spring Data Redis 的 Repository 支持
@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {
    Optional<RefreshToken> findByUsername(String username);
    void deleteByUsername(String username);
}
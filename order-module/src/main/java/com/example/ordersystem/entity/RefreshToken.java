package com.example.ordersystem.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.time.Instant;

/**
 * Refresh Token 实体，存储在 Redis 中
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * @RedisHash 告诉 Spring Data Redis：该类的实例需要被存储到 Redis 中。
 * value = "refresh_token" 指定了 Redis 中 key 的前缀（即所有 RefreshToken 数据都放在以 refresh_token: 开头的 key 下）。
 * timeToLive 设置了整个 Redis Hash 的过期时间（单位秒），1 天后自动删除。
 * 每个 RefreshToken 实例在 Redis 中实际上被存储为一个 Hash 数据结构，其中：
 * Key 格式：refresh_token:<id>（id 字段的值，即 refresh token 字符串）
 * Hash 的 field 对应实体中的字段（如 username, type, userId, clientId, createdAt）
 * Hash 的 value 为各字段的具体值。
 */
@RedisHash(value = "refresh_token", timeToLive = 24 * 3600)  // TTL 1天
public class RefreshToken {
    //@Id：标注在 id 字段上，表示该字段是 Redis Hash 的 主键。存储时，完整的 Redis Key 为 refresh_token:{id}（例如 refresh_token:abc123）。
    @Id private String id;          // token 值（随机字符串）
    //@Indexed：标注在 username 字段上，表示为该字段创建二级索引，以便通过 username 快速查找对应的 RefreshToken。
    @Indexed private String username;    // 用户名（或 clientId）
    private String type;        // "user" 或 "client"
    private Long userId;        // 仅 type=user 时有值
    private String clientId;    // 仅 type=client 时有值
    private Instant createdAt;
}
package com.example.ordersystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户实体类，对应数据库表 t_user
 */
@Entity
@Table(name = "t_user")
@Data
@NoArgsConstructor
public class User {
    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                    // 主键ID

    @Column(unique = true, nullable = false)
    private String username;            // 用户名（唯一）

    @Column(nullable = false)
    private String password;            // 密码（BCrypt 加密存储）

    // 原有字段不变，新增以下：
    @Column(nullable = false)
    private Integer availableStock = 1000;

    // 普通字段，不使用 @Version
    @Column
    private Integer version;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
package com.example.ordersystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 订单实体类，对应数据库表 t_order
 */
@Entity
@Table(name = "t_order")
@Data
@NoArgsConstructor
public class Order {
    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                // 主键ID

    @Column(unique = true, nullable = false)
    private String orderNo;         // 订单号（唯一）

    @Column(nullable = false)
    private Long userId;            // 下单用户ID，关联 t_user.id

    private String productName;     // 商品名称
    private Integer quantity;       // 数量

    // 在 Order 实体中增加常量
    public static final int STATUS_PAYING = 2;   // 支付中
    public static final int STATUS_NORMAL = 0;   // 正常（下单成功）
    public static final int STATUS_CANCELLED = 1; // 已取消
    private Integer status;         // 订单状态：0-正常，1-已取消

    @CreationTimestamp
    private LocalDateTime createTime;   // 创建时间（自动生成）
}
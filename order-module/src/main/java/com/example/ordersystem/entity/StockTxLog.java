package com.example.ordersystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_tx_log")
@Data
public class StockTxLog {

    @Id
    @Column(length = 64)
    private String txId;          // 事务ID（手动赋值，不自动生成）

    private Long orderId;

    private Long userId;

    private String operation;     // CREATE / CANCEL

    private Integer amount;

    private String status;        // COMMIT, ROLLBACK, UNKNOWN

    @CreationTimestamp
    private LocalDateTime createTime;
}
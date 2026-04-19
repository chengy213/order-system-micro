package com.example.ordersystem.messaging.common;

import com.example.ordersystem.entity.OperationLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Kafka 消息体，包装 OperationLog 实体
 * 同时携带唯一消息 ID 用于幂等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderOperationEvent implements Serializable {
    private String messageId;          // 雪花算法生成，全局唯一，用于幂等
    private OperationLog operationLog; // 订单操作日志实体
}
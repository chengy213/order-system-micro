package com.example.ordersystem.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 自定义雪花算法，支持嵌入基因（7位）在最低位
 * 位数分配（从高位到低位）：
 *   41位时间戳 + 8位工作机器ID + 8位序列号 + 7位基因 = 64位
 * 其中订单和库存日志的ID生成时基因 = userId % 100（0-99）
 */
@Component
public class GeneSnowflake {
    // 位数常量
    private static final long TIMESTAMP_BITS = 41L;
    private static final long WORKER_ID_BITS = 8L;
    private static final long SEQUENCE_BITS = 8L;
    private static final long GENE_BITS = 7L;

    // 最大值
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);   // 255
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);      // 255
    private static final long MAX_GENE = ~(-1L << GENE_BITS);              // 127

    // 移位位数（从低位向高位计算）
    private static final long SEQUENCE_SHIFT = GENE_BITS;                  // 序列号左移7位
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS + GENE_BITS; // 机器ID左移15位
    private static final long TIMESTAMP_SHIFT = WORKER_ID_BITS + SEQUENCE_BITS + GENE_BITS; // 时间戳左移23位

    // 起始时间戳（2024-01-01 00:00:00 UTC）
    private static final long TWEPOCH = 1704067200000L;

    private long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public GeneSnowflake(@Value("${snowflake.worker-id:1}") long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(String.format("workerId must be between 0 and %d", MAX_WORKER_ID));
        }
        this.workerId = workerId;
    }

    /**
     * 生成带基因的ID（基因放在最低7位）
     * @param gene 基因值（0-127），通常为 userId % 100
     */
    public synchronized long nextId(long gene) {
        if (gene < 0 || gene > MAX_GENE) {
            throw new IllegalArgumentException(String.format("gene must be between 0 and %d", MAX_GENE));
        }
        long timestamp = System.currentTimeMillis();
        // 处理时钟回拨（简单回拨等待）
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) { // 容忍 5 毫秒以内的回拨，等待追上
                try {
                    wait(offset << 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                timestamp = lastTimestamp;
            } else {
                throw new RuntimeException("Clock moved backwards. Refusing to generate id");
            }
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;

        long timestampPart = (timestamp - TWEPOCH) & ((1L << TIMESTAMP_BITS) - 1);
        // 按顺序组合：时间戳 | 机器ID | 序列号 | 基因
        return (timestampPart << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | (sequence << SEQUENCE_SHIFT)
                | gene;
    }

    /**
     * 无基因参数时默认基因=0（用于用户表主键等无需基因的场景）
     */
    public long nextId() {
        return nextId(0);
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
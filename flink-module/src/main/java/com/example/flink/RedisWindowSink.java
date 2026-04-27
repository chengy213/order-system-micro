package com.example.flink;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * 将窗口聚合结果写入 Redis。
 * Key 格式：stats:{operation}:{windowSize}:{windowEndTimestamp}
 * Value：计数（字符串）
 * 过期时间：1小时
 */
public class RedisWindowSink extends RichSinkFunction<Tuple2<String, Long>> {
    private final String windowSize;   // "15min" 或 "1hour"
    private transient JedisPool jedisPool;

    public RedisWindowSink(String windowSize) {
        this.windowSize = windowSize;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        // 请根据实际 Redis 地址修改（本地默认为 localhost:6379）
        jedisPool = new JedisPool(poolConfig, "localhost", 6379, 2000);
    }

    @Override
    public void invoke(Tuple2<String, Long> value, Context context) throws Exception {
        // 获取当前窗口的结束时间（近似，使用系统当前时间戳）
        long windowEndSeconds = System.currentTimeMillis() / 1000;
        String key = String.format("stats:%s:%s:%d", value.f0, windowSize, windowEndSeconds);
        try (Jedis jedis = jedisPool.getResource()) {
            // 设置过期时间为 1 小时，避免占用过多内存
            jedis.setex(key, 3600, String.valueOf(value.f1));
        }
    }

    @Override
    public void close() throws Exception {
        if (jedisPool != null) {
            jedisPool.close();
        }
        super.close();
    }
}
package com.example.ordersystem.config;//package com.example.ordersystem.config;
//
//import com.example.ordersystem.util.GeneSnowflake;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class SnowflakeIdConfig {
//
//    @Value("${snowflake.worker-id:1}")
//    private long workerId;
//
//    @Value("${snowflake.datacenter-id:1}")
//    private long datacenterId;
//
//    @Bean
//    public GeneSnowflake snowflake() {
//        // 使用配置的机器ID和数据中心ID初始化雪花算法生成器
//        return new GeneSnowflake(workerId);
//    }
//}
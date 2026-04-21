package com.example.ordersystem;

import io.seata.spring.annotation.datasource.EnableAutoDataSourceProxy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient   // 启用 Nacos 服务发现
@EnableFeignClients
@EnableAutoDataSourceProxy   // 强制开启数据源代理
public class OrderSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderSystemApplication.class, args);
    }
}
package com.example.ordersystem.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /**
     * 订单操作日志主题
     * 分区数设为 3（可根据需要调整）
     * 生产者在发送消息时使用 orderId 作为 key，确保同一订单的消息进入同一分区
     * 消费者每个分区单线程消费，保证同一订单的消息严格有序
     */
    @Bean
    public NewTopic orderOperationTopic() {
        return TopicBuilder.name("order-operations")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
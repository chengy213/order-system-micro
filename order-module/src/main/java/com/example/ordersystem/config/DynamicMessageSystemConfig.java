package com.example.ordersystem.config;

import com.example.ordersystem.messaging.common.MessagePublisher;
import com.example.ordersystem.messaging.kafka.KafkaEventPublisher;
import com.example.ordersystem.messaging.rocketmq.RocketMQEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@RefreshScope
public class DynamicMessageSystemConfig {

    @Value("${message.system:kafka}")
    private String messageSystem;

    @Autowired
    private KafkaEventPublisher kafkaPublisher;

    @Autowired
    private RocketMQEventPublisher rocketMQPublisher;

    @Bean
    @Primary
    @RefreshScope   // 关键：当 message.system 变化时，此 Bean 会重新创建
    public MessagePublisher messagePublisher() {
        // 也可以通过ApplicationContext.getBean("kafkaEventPublisher", MessagePublisher.class);
        if ("rocketmq".equalsIgnoreCase(messageSystem)) {
            return rocketMQPublisher;
        } else {
            return kafkaPublisher;   // 默认 kafka
        }
    }
}
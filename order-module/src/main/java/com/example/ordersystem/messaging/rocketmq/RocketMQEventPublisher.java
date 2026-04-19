package com.example.ordersystem.messaging.rocketmq;

import com.example.ordersystem.messaging.common.MessagePublisher;
import com.example.ordersystem.messaging.common.OrderOperationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RocketMQEventPublisher implements MessagePublisher {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    // 使用支持 Java 8 时间类型的 ObjectMapper
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final String TOPIC = "order-operations";

    @Override
    public void publish(OrderOperationEvent event, String partitionKey) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            // 关键：设置 KEYS 属性，用于顺序消费和消息检索
            // partitionKey 是业务键（订单ID或用户ID），相同键的消息将进入同一队列，保证顺序
            Message<String> message = MessageBuilder.withPayload(payload)
                    .setHeader("KEYS", partitionKey)
                    .build();

            rocketMQTemplate.syncSend(TOPIC, message);
            log.info("RocketMQ 消息发送成功: messageId={}, partitionKey={}, topic={}",
                    event.getMessageId(), partitionKey, TOPIC);
        } catch (JsonProcessingException e) {
            log.error("RocketMQ 消息序列化失败: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("RocketMQ 消息发送失败: {}", e.getMessage(), e);
        }
    }
}
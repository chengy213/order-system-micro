package com.example.ordersystem.messaging.kafka;

import com.example.ordersystem.messaging.common.MessagePublisher;
import com.example.ordersystem.messaging.common.OrderOperationEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class KafkaEventPublisher implements MessagePublisher {

    @Autowired
    private KafkaTemplate<String, OrderOperationEvent> kafkaTemplate;

    private static final String TOPIC = "order-operations";

    /**
     * 发送订单操作事件到 Kafka
     * @param event 订单操作日志实体
     * @param partitionKey 分区键（用于保证顺序，可为 orderId 或 userId）
     */
    @Override
    public void publish(OrderOperationEvent event, String partitionKey) {
        try {
            // 1. 创建 ProducerRecord，指定 Topic, Key, Value
            ProducerRecord<String, OrderOperationEvent> record =
                    new ProducerRecord<>(TOPIC, partitionKey, event);

            // 2. 添加自定义 Headers 进行分类
            record.headers()
                    // 添加事件类型，如：order_created, order_paid, order_shipped
                    .add("routingId", event.getMessageId().getBytes(StandardCharsets.UTF_8))
                    // 添加事件版本号，方便未来升级
                    .add("version", "1.0".getBytes(StandardCharsets.UTF_8));
            // 可以按需添加更多 Header，例如租户ID用于多租户场景
            // .add("tenantId", event.getTenantId().getBytes(StandardCharsets.UTF_8));

            // 3. 发送消息
            kafkaTemplate.send(record);
//            kafkaTemplate.send(TOPIC, partitionKey, event);
            log.info("Kafka 消息发送成功: messageId={}, partitionKey={}",
                    event.getMessageId(), partitionKey);
        } catch (Exception e) {
            log.error("Kafka 消息发送失败: {}", e.getMessage(), e);
        }
    }
}
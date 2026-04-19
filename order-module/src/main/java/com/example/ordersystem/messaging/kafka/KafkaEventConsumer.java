package com.example.ordersystem.messaging.kafka;

import com.example.ordersystem.entity.OperationLog;
import com.example.ordersystem.messaging.common.OrderOperationEvent;
import com.example.ordersystem.repository.OperationLogElasticsearchRepository;
import com.example.ordersystem.util.IdempotentHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaEventConsumer {

    @Autowired
    private OperationLogElasticsearchRepository esRepository;

    @Autowired
    private IdempotentHelper idempotentHelper;

    private static final long TTL_SECONDS = 3600; // 幂等记录保留1小时

    @KafkaListener(topics = "order-operations", groupId = "order-log-group")
    public void consume(@Payload OrderOperationEvent event,
                        @Header(value = "routingId", required = false) String routingId,
                        @Header(value = "version", required = false) String version,
                        Acknowledgment acknowledgment) {
        String messageId = event.getMessageId();
        // 幂等检查
        String key = "kafka:" + messageId;  // 加入前缀区分来源
        // 尝试获取处理权（过期时间 1 小时）
        if (!idempotentHelper.tryProcess(key, 3600)) {
            log.info("Kafka 消息正在处理或已处理过，忽略: messageId={}", messageId);
            acknowledgment.acknowledge(); // 避免重复拉取
            return;
        }

        try {
            OperationLog logDoc = event.getOperationLog();
            esRepository.save(logDoc);
            log.info("Kafka 消费者写入 ES 成功: messageId={}, routingId:{}, version:{}, docId={}", messageId, routingId, version, logDoc.getId());

            // 标记为已处理
            idempotentHelper.markSuccess(key);
            // 手动确认
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Kafka 消费者写入 ES 失败: messageId={}, error={}", messageId, e.getMessage(), e);
            // 不确认，消息会重新投递（依赖 Kafka 重试）
        }
    }
}
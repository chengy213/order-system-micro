package com.example.ordersystem.messaging.rocketmq;

import com.example.ordersystem.messaging.common.OrderOperationEvent;
import com.example.ordersystem.repository.OperationLogElasticsearchRepository;
import com.example.ordersystem.util.IdempotentHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class RocketMQEventConsumer {

    @Autowired
    private DefaultMQPushConsumer rocketMQConsumer;

    @Autowired
    private OperationLogElasticsearchRepository esRepository;

    @Autowired
    private IdempotentHelper idempotentHelper;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Value("${rocketmq.consumer.max-reconsume-times:16}")
    private int maxReconsumeTimes;

    @PostConstruct
    public void start() throws Exception {
        // 配置消费者行为
        rocketMQConsumer.setConsumeThreadMin(1);
        rocketMQConsumer.setConsumeThreadMax(1);   // 单线程保证分区内顺序
        rocketMQConsumer.setMaxReconsumeTimes(maxReconsumeTimes); // 最大重试次数

        rocketMQConsumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs,
                                                            ConsumeConcurrentlyContext context) {
                for (MessageExt msg : msgs) {
                    try {
                        String body = new String(msg.getBody());
                        OrderOperationEvent event = objectMapper.readValue(body, OrderOperationEvent.class);
                        String messageId = event.getMessageId();

                        // 幂等检查
                        String key = "rocketmq:log:" + messageId;

                        if (!idempotentHelper.tryProcess(key, 3600)) {
                            log.info("RocketMQ 普通消息正在处理或已处理过，忽略: messageId={}", messageId);
                            return ConsumeConcurrentlyStatus.RECONSUME_LATER; // 自动确认
                        }

                        // 写入 Elasticsearch
                        esRepository.save(event.getOperationLog());
                        idempotentHelper.markSuccess(key);
                        log.info("RocketMQ 消费者写入 ES 成功: messageId={}, orderId={}",
                                messageId, event.getOperationLog().getId());

                    } catch (Exception e) {
                        log.error("RocketMQ 消息处理失败: messageId={}, reconsumeTimes={}, error={}",
                                getMessageIdFromMsg(msg), msg.getReconsumeTimes(), e.getMessage(), e);

                        // 超过最大重试次数则不再重试，记录死信并返回成功（避免无限重试）
                        if (msg.getReconsumeTimes() >= maxReconsumeTimes) {
                            log.error("消息进入死信队列，放弃重试: msgId={}", msg.getMsgId());
                            // 可选：将死信写入专门索引或发送告警
                            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                        }
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        rocketMQConsumer.start();
        log.info("RocketMQ consumer started with group: {}, namesrv: {}",
                rocketMQConsumer.getConsumerGroup(), rocketMQConsumer.getNamesrvAddr());
    }

    private String getMessageIdFromMsg(MessageExt msg) {
        try {
            String body = new String(msg.getBody());
            OrderOperationEvent event = objectMapper.readValue(body, OrderOperationEvent.class);
            return event.getMessageId();
        } catch (Exception e) {
            return msg.getMsgId();
        }
    }

    @PreDestroy
    public void stop() {
        if (rocketMQConsumer != null) {
            rocketMQConsumer.shutdown();
            log.info("RocketMQ consumer stopped");
        }
    }
}
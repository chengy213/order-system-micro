package com.example.ordersystem.messaging.rocketmq;

import com.example.ordersystem.util.GeneSnowflake;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class StockTransactionMessagePublisher {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private GeneSnowflake geneSnowflake;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final String TX_TOPIC = "order-stock-tx";

    /**
     * 发送创建订单事务消息
     * @param userId 用户ID
     * @param productName 商品名称
     * @param quantity 数量
     */
    public void sendCreateOrderTransaction(Long userId, String productName, Integer quantity) {
        String trackingId = MDC.get("trackingId");
        if (trackingId == null) trackingId = "unknown";

        String txId = String.valueOf(geneSnowflake.nextId(Objects.isNull(userId) ? 0L : userId%100));

        Map<String, Object> payload = new HashMap<>();
        payload.put("txId", txId);
        payload.put("userId", userId);
        payload.put("productName", productName);
        payload.put("quantity", quantity);
        payload.put("operation", "CREATE");
        payload.put("trackingId", trackingId);

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("序列化消息体失败", e);
            throw new RuntimeException(e);
        }

        Message<String> message = MessageBuilder.withPayload(jsonPayload)
                .setHeader("KEYS", String.valueOf(userId))   // 保证同一用户顺序
                .setHeader("BIZ_TX_ID", txId)
                .build();

        // 发送事务消息（tag 区分操作类型），同步等待本地事务完成
        //sendStockTransaction 方法内部调用了 RocketMQ 的 sendMessageInTransaction，该方法会阻塞等待本地事务执行完成并返回结果。
        //所以下面的info日志会晚于com.example.ordersystem.config.StockTransactionListener.executeLocalTransaction中的日志输出
        TransactionSendResult sendResult = rocketMQTemplate.sendMessageInTransaction(TX_TOPIC + ":create", message, payload);
        logTransactionResult("创建订单", txId, userId, sendResult);
    }

    /**
     * 发送取消订单事务消息
     * @param orderId 订单ID
     * @param userId 用户ID
     */
    public void sendCancelOrderTransaction(Long orderId, Long userId) {
        String trackingId = MDC.get("trackingId");
        if (trackingId == null) trackingId = "unknown";

        String txId = String.valueOf(geneSnowflake.nextId(Objects.isNull(userId) ? 0L : userId%100));

        Map<String, Object> payload = new HashMap<>();
        payload.put("txId", txId);
        payload.put("orderId", orderId);
        payload.put("userId", userId);
        payload.put("operation", "CANCEL");
        payload.put("trackingId", trackingId);

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("序列化消息体失败", e);
            throw new RuntimeException(e);
        }

        Message<String> message = MessageBuilder.withPayload(jsonPayload)
                .setHeader("KEYS", String.valueOf(userId))
                .setHeader("BIZ_TX_ID", txId)
                .build();

        TransactionSendResult sendResult = rocketMQTemplate.sendMessageInTransaction(TX_TOPIC + ":cancel", message, payload);
        logTransactionResult("取消订单", txId, userId, sendResult);
    }

    /**
     * 统一处理事务消息发送结果的日志
     */
    private void logTransactionResult(String operationDesc, String txId, Long userId, TransactionSendResult sendResult) {
        if (sendResult == null) {
            log.error("{}事务消息发送失败: 返回结果为空, txId={}, userId={}", operationDesc, txId, userId);
            return;
        }
        var localState = sendResult.getLocalTransactionState();
        var sendStatus = sendResult.getSendStatus();
        switch (localState) {
            case COMMIT_MESSAGE:
                log.info("{}事务消息已提交: txId={}, userId={}, sendStatus={}", operationDesc, txId, userId, sendStatus);
                break;
            case ROLLBACK_MESSAGE:
                log.warn("{}事务消息已回滚: txId={}, userId={}, sendStatus={}", operationDesc, txId, userId, sendStatus);
                break;
            case UNKNOW:
                log.warn("{}事务消息状态未知: txId={}, userId={}, sendStatus={}", operationDesc, txId, userId, sendStatus);
                break;
            default:
                log.info("{}事务消息已发送: txId={}, userId={}, localState={}, sendStatus={}",
                        operationDesc, txId, userId, localState, sendStatus);
        }
    }
}
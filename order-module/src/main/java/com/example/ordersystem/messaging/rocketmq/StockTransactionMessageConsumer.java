package com.example.ordersystem.messaging.rocketmq;

import com.example.ordersystem.entity.OperationLog;
import com.example.ordersystem.entity.StockTxLog;
import com.example.ordersystem.repository.OperationLogElasticsearchRepository;
import com.example.ordersystem.repository.StockTxLogRepository;
import com.example.ordersystem.util.IdempotentHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "order-stock-tx",
        consumerGroup = "stock-tx-consumer-group",
        selectorExpression = "create || cancel"  // 消费 create 和 cancel 两个 tag
)
public class StockTransactionMessageConsumer implements RocketMQListener<MessageExt> {

    @Autowired
    private OperationLogElasticsearchRepository esRepository;

    @Autowired
    private StockTxLogRepository stockTxLogRepository;

    @Autowired
    private IdempotentHelper idempotentHelper;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Override
    public void onMessage(MessageExt message) {
        String txId = null;
        try {
            // 解析消息体（JSON 字符串）
            String body = new String(message.getBody());
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(body, Map.class);

            // 提取业务参数（与发送时一致）
            txId = (String) payload.get("txId");
            Long orderId = payload.get("orderId") != null ? Long.valueOf(payload.get("orderId").toString()) : null;
            Long userId = payload.get("userId") != null ? Long.valueOf(payload.get("userId").toString()) : null;
            String operation = (String) payload.get("operation");
            if (StringUtils.equalsIgnoreCase(operation, "CREATE")) {
                operation = "DECREASE";
            } else if (StringUtils.equalsIgnoreCase(operation, "CANCEL")) {
                operation = "INCREASE";
            }
            Integer amount = payload.get("amount") != null ? Integer.valueOf(payload.get("amount").toString()) : null;
            String trackingId = (String) payload.get("trackingId");

            if (txId == null) {
                log.error("消息中缺少 txId，无法处理");
                return;
            }

            // 幂等检查（基于 txId）
            String key = "stock:tx:" + txId;
            if (!idempotentHelper.tryProcess(key, 3600)) {
                log.info("库存事务消息正在处理或已处理过，忽略: txId={}", txId);
                return;
            }

            // 可选：再次确认本地事务状态（从 stock_tx_log 表）
            StockTxLog txLog = stockTxLogRepository.findByTxId(txId).orElse(null);
            if (txLog == null) {
                log.warn("未找到库存事务日志，可能事务未执行完成，暂不处理: txId={}", txId);
                return;
            }
            if (!"COMMIT".equals(txLog.getStatus())) {
                log.warn("库存事务状态不是 COMMIT，忽略消费: txId={}, status={}", txId, txLog.getStatus());
                return;
            }

            // 构造 OperationLog 对象，写入 ES
            String docId = String.format("%s_%s_%s",
                    trackingId != null ? trackingId : "unknown",
                    "STOCK_" + operation,
                    LocalDateTime.now(ZoneOffset.UTC).format(TIME_FORMATTER));

            OperationLog logDoc = OperationLog.builder()
                    .id(docId)
                    .trackingId(trackingId)
                    .userId(userId != null ? String.valueOf(userId) : "")
                    .username("")   // 库存操作没有用户名，留空
                    .operation("STOCK_" + operation)  // 例如 STOCK_CREATE 或 STOCK_CANCEL
                    .details(String.format("订单ID:%d, 变动数量:%d", orderId, amount))
                    .ipAddress("")   // 库存操作无客户端 IP
                    .userAgent("")
                    .operationTime(LocalDateTime.now(ZoneOffset.UTC))
                    .build();

            esRepository.save(logDoc);
            // 标记幂等
            idempotentHelper.markSuccess(key);
            log.info("库存事务消息消费成功，写入ES: txId={}, operation={}, userId={}, orderId={}",
                    txId, operation, userId, orderId);
        } catch (Exception e) {
            log.error("消息体解析失败，可能是旧格式，跳过此消息: msgId={}, body={}", message.getMsgId(), new String(message.getBody()), e);
            // 直接返回，RocketMQ 会认为消费成功，不再重试
            return;
        }
    }
}
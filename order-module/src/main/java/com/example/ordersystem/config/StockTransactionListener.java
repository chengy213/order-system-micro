package com.example.ordersystem.config;

import com.example.ordersystem.repository.StockTxLogRepository;
import com.example.ordersystem.service.OrderStockTransactionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RocketMQ 事务消息监听器
 * 负责执行本地事务（订单+库存）和事务回查
 */
@Slf4j
@Component
@RocketMQTransactionListener
public class StockTransactionListener implements RocketMQLocalTransactionListener {

    @Autowired
    private OrderStockTransactionService orderStockTxService;

    @Autowired
    private StockTxLogRepository stockTxLogRepository;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        Map<String, Object> params = (Map<String, Object>) arg;
        String txId = (String) params.get("txId");
        String operation = (String) params.get("operation");
        Long userId = Long.valueOf(params.get("userId").toString());

        try {
            // 幂等检查：如果事务日志已存在（可能是回查场景），直接返回对应状态
            if (stockTxLogRepository.findByTxId(txId).isPresent()) {
                log.warn("事务已处理过，忽略: txId={}", txId);
                // 这里应该根据已有日志的状态返回 COMMIT 或 ROLLBACK，但简单起见返回 COMMIT
                return RocketMQLocalTransactionState.COMMIT;
            }

            if ("CREATE".equals(operation)) {
                String productName = (String) params.get("productName");
                Integer quantity = (Integer) params.get("quantity");
                orderStockTxService.createOrderAndDecreaseStock(txId, userId, productName, quantity);
                return RocketMQLocalTransactionState.COMMIT;
            } else if ("CANCEL".equals(operation)) {
                Long orderId = Long.valueOf(params.get("orderId").toString());
                orderStockTxService.cancelOrderAndIncreaseStock(txId, orderId, userId);
                return RocketMQLocalTransactionState.COMMIT;
            } else {
                log.error("未知操作类型: {}", operation);
                return RocketMQLocalTransactionState.ROLLBACK;
            }
        } catch (Exception e) {
            log.error("本地事务执行失败", e);
            // 事务日志已在服务方法中回滚，无需额外记录
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String txId = (String) msg.getHeaders().get("BIZ_TX_ID");
        if (txId == null) {
            log.error("回查时未获取到事务ID");
            return RocketMQLocalTransactionState.UNKNOWN;
        }
        var txLog = stockTxLogRepository.findByTxId(txId);
        if (txLog.isEmpty()) {
            return RocketMQLocalTransactionState.UNKNOWN;
        }
        if ("COMMIT".equals(txLog.get().getStatus())) {
            return RocketMQLocalTransactionState.COMMIT;
        } else if ("ROLLBACK".equals(txLog.get().getStatus())) {
            return RocketMQLocalTransactionState.ROLLBACK;
        } else {
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }
}
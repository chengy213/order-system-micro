package com.example.ordersystem.service;

import com.example.ordersystem.client.PayClient;
import com.example.ordersystem.entity.Order;
import com.example.ordersystem.entity.StockTxLog;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.StockTxLogRepository;
import com.example.ordersystem.util.GeneSnowflake;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * 订单与库存的组合事务服务
 * 保证订单创建/取消与库存操作的原子性，同时记录事务日志
 */
@Slf4j
@Service
public class OrderStockTransactionService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private StockTxLogRepository stockTxLogRepository;

    @Autowired
    private OrderService orderService;  // 用于清理订单缓存

    @Autowired
    private GeneSnowflake geneSnowflake;

    // 在 OrderStockTransactionService 中注入 PayClient 和动态配置
    @Autowired
    private PayClient payClient;

    @Value("${payment.check.enabled:true}")
    private boolean paymentCheckEnabled;

    /**
     * 创建订单并扣减库存，同时记录事务日志（原子操作）
     * @param txId 事务ID（雪花算法生成）
     * @param userId 用户ID
     * @param productName 商品名称
     * @param quantity 数量
     * @return 订单对象
     * @throws RuntimeException 库存不足或其他异常
     */
    @Transactional(rollbackFor = Exception.class)
    public Order createOrderAndDecreaseStock(String txId, Long userId, String productName, Integer quantity) {
        // 1. 创建订单
        Order order = new Order();
        order.setId(geneSnowflake.nextId(Objects.isNull(userId) ? 0L : userId%100));
        order.setOrderNo(orderService.generateOrderNo());
        order.setUserId(userId);
        order.setProductName(productName);
        order.setQuantity(quantity);
        order.setStatus(Order.STATUS_PAYING);   // 新状态
        order.setCreateTime(LocalDateTime.now(ZoneOffset.UTC));
        Order saved = orderRepository.save(order);

        // 2. 扣减库存
        boolean success = stockService.decreaseStock(userId, quantity);
        if (!success) {
            throw new RuntimeException("库存不足");
        }

        // 3. 支付检查（如果开关开启）
        if (paymentCheckEnabled) {
            boolean paySuccess = payClient.checkPayment(saved.getId(), userId, productName, quantity);
            if (!paySuccess) {
                throw new RuntimeException("支付检查失败：商品数量超过限制");
            }
        }

        // 4. 更新订单状态为正常（下单成功）
        saved.setStatus(Order.STATUS_NORMAL);
        orderRepository.save(saved);

        // 5. 清理缓存
        orderService.clearUserOrderCache(userId);
        // 6. 记录事务日志
        saveTxLog(txId, saved.getId(), userId, "CREATE", quantity, "COMMIT");

        log.info("创建订单并扣减库存成功: txId={}, userId={}, orderId={}, quantity={}",
                txId, userId, saved.getId(), quantity);
        return saved;
    }

    /**
     * 取消订单并回补库存，同时记录事务日志（原子操作）
     * @param txId 事务ID
     * @param orderId 订单ID
     * @param userId 用户ID
     * @return 订单对象
     * @throws RuntimeException 订单不存在、已取消或无权限
     */
    @Transactional(rollbackFor = Exception.class)
    public Order cancelOrderAndIncreaseStock(String txId, Long orderId, Long userId) {
        // 1. 取消订单
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权限取消该订单");
        }
        if (order.getStatus() == 1) {
            throw new RuntimeException("订单已取消");
        }
        order.setStatus(1);
        Order saved = orderRepository.save(order);

        // 2. 回补库存
        stockService.increaseStock(userId, order.getQuantity());

        // 3. 记录事务日志（状态 COMMIT）
        saveTxLog(txId, orderId, userId, "CANCEL", order.getQuantity(), "COMMIT");

        // 4. 清理该用户的订单缓存
        orderService.clearUserOrderCache(userId);

        log.info("取消订单并回补库存成功: txId={}, userId={}, orderId={}, quantity={}",
                txId, userId, orderId, order.getQuantity());
        return saved;
    }

    /**
     * 保存事务日志到数据库
     */
    private void saveTxLog(String txId, Long orderId, Long userId, String operation, Integer amount, String status) {
        StockTxLog logEntry = new StockTxLog();
        logEntry.setTxId(txId);
        logEntry.setOrderId(orderId);
        logEntry.setUserId(userId);
        logEntry.setOperation(operation);
        logEntry.setAmount(amount);
        logEntry.setStatus(status);
        logEntry.setCreateTime(LocalDateTime.now(ZoneOffset.UTC));
        stockTxLogRepository.save(logEntry);
    }
}
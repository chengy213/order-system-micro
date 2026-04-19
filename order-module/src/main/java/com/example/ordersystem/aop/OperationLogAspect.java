package com.example.ordersystem.aop;

import com.example.ordersystem.entity.OperationLog;
import com.example.ordersystem.messaging.common.MessagePublisher;
import com.example.ordersystem.messaging.common.OrderOperationEvent;
import com.example.ordersystem.security.SecurityUser;
import com.example.ordersystem.util.GeneSnowflake;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

/**
 * 操作日志切面
 * 仅在订单创建/取消操作成功时记录日志（发送消息到 Kafka/RocketMQ）
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Autowired
    private MessagePublisher messagePublisher;

    @Autowired
    private GeneSnowflake geneSnowflake;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    // Web 端下单切入点
    @Pointcut("execution(* com.example.ordersystem.controller.OrderController.createOrder(..))")
    public void createOrderPointcut() {}

    // Web 端取消订单切入点
    @Pointcut("execution(* com.example.ordersystem.controller.OrderController.cancelOrder(..))")
    public void cancelOrderPointcut() {}

    // API 端下单切入点
    @Pointcut("execution(* com.example.ordersystem.controller.ApiController.createOrderForUser(..))")
    public void apiCreateOrderPointcut() {}

    // API 端取消订单切入点
    @Pointcut("execution(* com.example.ordersystem.controller.ApiController.cancelOrderForUser(..))")
    public void apiCancelOrderPointcut() {}

    /**
     * Web 端下单成功后的日志记录
     */
    @AfterReturning(pointcut = "createOrderPointcut()", returning = "result")
    public void afterCreateOrder(JoinPoint joinPoint, Object result) {
        if (isWebOperationSuccess(joinPoint, result)) {
            recordOrderOperation("CREATE_ORDER", joinPoint);
        }
    }

    /**
     * Web 端取消订单成功后的日志记录
     */
    @AfterReturning(pointcut = "cancelOrderPointcut()", returning = "result")
    public void afterCancelOrder(JoinPoint joinPoint, Object result) {
        if (isWebOperationSuccess(joinPoint, result)) {
            recordOrderOperation("CANCEL_ORDER", joinPoint);
        }
    }

    /**
     * API 端下单成功后的日志记录
     */
    @AfterReturning(pointcut = "apiCreateOrderPointcut()", returning = "result")
    public void afterApiCreateOrder(JoinPoint joinPoint, Object result) {
        if (isApiOperationSuccess(result)) {
            recordOrderOperation("CREATE_ORDER", joinPoint);
        }
    }

    /**
     * API 端取消订单成功后的日志记录
     */
    @AfterReturning(pointcut = "apiCancelOrderPointcut()", returning = "result")
    public void afterApiCancelOrder(JoinPoint joinPoint, Object result) {
        if (isApiOperationSuccess(result)) {
            recordOrderOperation("CANCEL_ORDER", joinPoint);
        }
    }

    /**
     * 判断 Web 端操作是否成功
     * 通过检查 RedirectAttributes 中是否包含 "success" 属性
     */
    private boolean isWebOperationSuccess(JoinPoint joinPoint, Object result) {
        // 获取方法参数中的 RedirectAttributes
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof RedirectAttributes) {
                RedirectAttributes ra = (RedirectAttributes) arg;
                // 检查 flash 属性中是否包含 success
                Map<String, ?> flashMap = ra.getFlashAttributes();
                if (flashMap != null && flashMap.containsKey("success")) {
                    return true;
                }
                // 也可以检查 attributes 中的 success（重定向前暂存）
                // 这里简单处理：只要有 success 就认为成功
            }
        }
        return false;
    }

    /**
     * 判断 API 端操作是否成功
     * 通过检查 ResponseEntity 的状态码和响应体中的错误字段
     */
    private boolean isApiOperationSuccess(Object result) {
        if (result instanceof ResponseEntity) {
            ResponseEntity<?> response = (ResponseEntity<?>) result;
            if (response.getStatusCode().is2xxSuccessful()) {
                Object body = response.getBody();
                if (body instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) body;
                    // 如果响应体中包含 error 字段且不为空，则认为失败
                    if (map.containsKey("error") && map.get("error") != null) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 记录订单操作日志（发送消息）
     */
    private void recordOrderOperation(String operation, JoinPoint joinPoint) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof SecurityUser)) {
                log.warn("无法获取当前登录用户，跳过操作日志记录");
                return;
            }
            SecurityUser user = (SecurityUser) auth.getPrincipal();

            // 解析方法参数
            Object[] args = joinPoint.getArgs();
            String details = "";
            String partitionKey = null;

            if ("CREATE_ORDER".equals(operation)) {
                // 参数顺序：productName, quantity, ... (Web 端可能还有 RedirectAttributes)
                String productName = null;
                Integer quantity = null;
                for (Object arg : args) {
                    if (arg instanceof String && productName == null) {
                        productName = (String) arg;
                    } else if (arg instanceof Integer && quantity == null) {
                        quantity = (Integer) arg;
                    }
                }
                if (productName != null && quantity != null) {
                    details = String.format("商品:%s,数量:%d", productName, quantity);
                    partitionKey = String.valueOf(user.getUserId());
                }
            } else if ("CANCEL_ORDER".equals(operation)) {
                Long orderId = null;
                for (Object arg : args) {
                    if (arg instanceof Long) {
                        orderId = (Long) arg;
                        break;
                    }
                }
                if (orderId != null) {
                    details = String.format("订单ID:%d", orderId);
                    partitionKey = String.valueOf(orderId);
                }
            }

            // 获取客户端信息
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            String ip = "", userAgent = "";
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                ip = request.getRemoteAddr();
                userAgent = request.getHeader("User-Agent");
            }

            // 生成文档 ID 和操作日志对象
            String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(TIME_FORMATTER);
            String trackingId = org.slf4j.MDC.get("trackingId");
            if (trackingId == null) trackingId = "unknown";
            String docId = String.format("%s_%s_%s", trackingId, operation, timestamp);

            OperationLog logDoc = OperationLog.builder()
                    .id(docId)
                    .trackingId(trackingId)
                    .userId(String.valueOf(user.getUserId()))
                    .username(user.getUsername())
                    .operation(operation)
                    .details(details)
                    .ipAddress(ip)
                    .userAgent(userAgent)
                    .operationTime(LocalDateTime.now(ZoneOffset.UTC))
                    .build();

            // 发送消息（Kafka 或 RocketMQ）
            String eventId = String.valueOf(geneSnowflake.nextId(Objects.isNull(user.getUserId()) ? 0L : user.getUserId()%100));
            OrderOperationEvent event = OrderOperationEvent.builder()
                    .messageId(eventId)
                    .operationLog(logDoc)
                    .build();

            messagePublisher.publish(event, partitionKey);
            log.info("操作日志已发送: operation={}, userId={}, trackingId={}", operation, user.getUserId(), trackingId);
        } catch (Exception e) {
            log.error("记录操作日志失败: {}", e.getMessage(), e);
        }
    }
}
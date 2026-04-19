package com.example.ordersystem.service;

import com.example.ordersystem.entity.OperationLog;
import com.example.ordersystem.repository.OperationLogElasticsearchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @deprecated 该service仅用在version7 - 加入@Async异步消费的实现里，在后面的version中已废弃不用
 */
@Slf4j
@Service
public class OperationLogService {

    @Autowired
    private OperationLogElasticsearchRepository esRepository;

    @Value("${operation.log.realtime.enabled:true}")
    private boolean realtimeEnabled;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * 异步记录订单操作日志
     * 1. 实时写入 Elasticsearch（使用自定义 docId）
     * 2. 输出纯 JSON 日志到文件，供 Logstash 消费（同样使用相同 docId，实现幂等覆盖）
     */
    @Async("logExecutor")
    public void logOrderOperation(String userId, String username, String operation, String details,
                                  String ipAddress, String userAgent) {
        String trackingId = MDC.get("trackingId");
        if (trackingId == null) trackingId = "unknown";

        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        String docId = String.format("%s_%s_%s", trackingId, operation, timestamp);

        OperationLog logDoc = OperationLog.builder()
                .id(docId)
                .trackingId(trackingId)
                .userId(userId)
                .username(username)
                .operation(operation)
                .details(details)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .operationTime(LocalDateTime.now(ZoneOffset.UTC))
                .build();

        // 实时写入 ES
        if (realtimeEnabled) {
            try {
                esRepository.save(logDoc);
                log.info("实时写入ES成功: operation={}, userId={}, docId={}", operation, userId, docId);
            } catch (Exception e) {
                log.error("实时写入ES失败: {}", e.getMessage(), e);
            }
        }

        // 输出纯 JSON 日志（供 Logstash 收集，与实时写入使用完全相同的 docId）
        try {
            String jsonLog = OBJECT_MAPPER.writeValueAsString(logDoc);
            // 使用标记 "ELK_OPERATION_LOG" 方便 Logstash 过滤
            log.info("ELK_OPERATION_LOG {}", jsonLog);
        } catch (Exception e) {
            log.error("序列化操作日志JSON失败: {}", e.getMessage());
        }
    }
}
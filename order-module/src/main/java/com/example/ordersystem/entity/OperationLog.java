package com.example.ordersystem.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志 ES 文档（订单操作专用）
 * 索引名称：order-operations
 * 文档 ID = trackingId + "_" + operation + "_" + 毫秒时间戳（保证唯一且幂等）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "order-operations", createIndex = true)
public class OperationLog implements Serializable {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String trackingId;

    @Field(type = FieldType.Keyword)
    private String userId;

    @Field(type = FieldType.Keyword)
    private String username;

    @Field(type = FieldType.Keyword)
    private String operation;   // CREATE_ORDER, CANCEL_ORDER

    @Field(type = FieldType.Text)
    private String details;

    @Field(type = FieldType.Keyword)
    private String ipAddress;

    @Field(type = FieldType.Keyword)
    private String userAgent;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime operationTime;
}
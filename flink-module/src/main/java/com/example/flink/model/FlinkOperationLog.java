package com.example.flink.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class FlinkOperationLog implements Serializable {
    private String id;
    private String trackingId;
    private String userId;
    private String username;
    private String operation;
    private String details;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime operationTime;

    public FlinkOperationLog() {
    }

    @Override
    public String toString() {
        return "OperationLog{" +
                "id='" + id + '\'' +
                ", trackingId='" + trackingId + '\'' +
                ", userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", operation='" + operation + '\'' +
                ", details='" + details + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", userAgent='" + userAgent + '\'' +
                ", operationTime=" + operationTime +
                '}';
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(String trackingId) {
        this.trackingId = trackingId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getOperationTime() {
        return operationTime;
    }

    public void setOperationTime(LocalDateTime operationTime) {
        this.operationTime = operationTime;
    }
}
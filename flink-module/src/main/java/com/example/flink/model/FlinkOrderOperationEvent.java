package com.example.flink.model;

import java.io.Serializable;

public class FlinkOrderOperationEvent implements Serializable {
    private String messageId;
    private FlinkOperationLog operationLog;

    public FlinkOrderOperationEvent() {
    }

    @Override
    public String toString() {
        return "OrderOperationEvent{" +
                "messageId='" + messageId + '\'' +
                ", operationLog=" + operationLog +
                '}';
    }

    // getters and setters
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public FlinkOperationLog getOperationLog() { return operationLog; }
    public void setOperationLog(FlinkOperationLog operationLog) { this.operationLog = operationLog; }
}
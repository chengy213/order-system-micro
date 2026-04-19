package com.example.ordersystem.messaging.common;

public interface MessagePublisher {
    void publish(OrderOperationEvent event, String partitionKey);
}
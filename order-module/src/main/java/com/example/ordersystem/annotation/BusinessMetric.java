package com.example.ordersystem.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BusinessMetric {
    String operation();          // 操作类型，如 register, login, createOrder, cancelOrder
    boolean recordFailureDetail() default true; // 失败时是否记录详细上下文
}
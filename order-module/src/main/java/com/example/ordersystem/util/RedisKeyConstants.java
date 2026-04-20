package com.example.ordersystem.util;

import java.util.concurrent.TimeUnit;

/**
 * Redis Key 常量管理类
 * 集中管理所有业务缓存的前缀及过期时间
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {} // 禁止实例化

    // ========== 用户相关缓存 ==========
    /** 用户库存缓存前缀：user:stock:{userId} */
    public static final String USER_STOCK_PREFIX = "user:stock:";
    /** 用户库存缓存过期时间（秒），默认3分钟 */
    public static final long USER_STOCK_TTL_SECONDS = 180L;

    /** 用户订单列表缓存前缀：orders:user:{userId} */
    public static final String USER_ORDERS_PREFIX = "orders:user:";
    /** 用户订单列表缓存过期时间（小时），默认1小时 */
    public static final long USER_ORDERS_TTL_HOURS = 1L;

    // ========== 其他业务缓存可按需添加 ==========
    // 例如：商品详情、SKU信息等
}
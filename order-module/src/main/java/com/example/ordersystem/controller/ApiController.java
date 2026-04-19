package com.example.ordersystem.controller;

import com.example.ordersystem.config.ClientProperties;
import com.example.ordersystem.entity.Order;
import com.example.ordersystem.entity.RefreshToken;
import com.example.ordersystem.messaging.rocketmq.StockTransactionMessagePublisher;
import com.example.ordersystem.security.ClientPrincipal;
import com.example.ordersystem.security.JwtUtil;
import com.example.ordersystem.service.OrderService;
import com.example.ordersystem.service.RefreshTokenService;
import com.example.ordersystem.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 提供给外部第三方调用的 REST API
 * 使用 JWT 认证（Authorization Header），基于 scopes 进行权限控制
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private StockService stockService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ClientProperties clientProperties;

    @Autowired
    private StockTransactionMessagePublisher stockTxPublisher;  // 事务消息发送器

    @Value("${jwt.expiration}")
    private Long accessTokenExpiration;

    /**
     * 客户端获取 JWT 令牌的接口（类似于 OAuth2 的客户端凭证模式）
     * @param clientId     客户端ID
     * @param clientSecret 客户端密钥
     * @return 包含 access_token 的 JSON 响应
     */
    @PostMapping("/token")
    public ResponseEntity<?> getToken(@RequestParam String clientId,
                                      @RequestParam String clientSecret) {
        ClientProperties.Client client = clientProperties.getClients().get(clientId);
        if (client != null && client.getSecret().equals(clientSecret)) {
            List<String> scopes = client.getScopes();
            // 生成客户端 JWT，包含 scopes 信息
            String accessToken = jwtUtil.generateTokenForClient(clientId, scopes);
            // 生成 Refresh Token
            RefreshToken refreshToken = refreshTokenService.createClientRefreshToken(clientId);
            // 也可以将 Refresh Token 放入 Cookie（API 客户端一般不用 Cookie），但为了统一，可以放在响应体中
            return ResponseEntity.ok(Map.of(
                    "access_token", accessToken,
                    "refresh_token", refreshToken.getId(),
                    "token_type", "Bearer",
                    "expires_in", accessTokenExpiration / 1000
            ));
        } else {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid client credentials"));
        }
    }

    /**
     * 获取指定用户的订单列表（只读）
     * 需要 SCOPE_read 权限
     */
    @GetMapping("/users/{userId}/orders")
    @PreAuthorize("hasAuthority('SCOPE_read')")
    public ResponseEntity<?> getUserOrders(@PathVariable Long userId,
                                           @AuthenticationPrincipal ClientPrincipal client) {
        // 记录调用日志
        System.out.println("Client " + client.getClientId() + " fetching orders for user " + userId);
        List<Order> orders = orderService.getOrdersByUserId(userId);
        // 可选：同时返回用户当前可用库存
        int availableStock = stockService.getAvailableStock(userId);
        return ResponseEntity.ok(Map.of("orders", orders, "availableStock", availableStock));
    }

    /**
     * 为指定用户创建新订单（读写）
     * 需要 SCOPE_write 权限
     * Version 10 变更：改为创建待确认订单 + 发送事务消息扣减库存
     */
    @PostMapping("/users/{userId}/orders")
    @PreAuthorize("hasAuthority('SCOPE_write')")
    public ResponseEntity<?> createOrderForUser(@PathVariable Long userId,
                                                @RequestBody Map<String, Object> payload,
                                                @AuthenticationPrincipal ClientPrincipal client) {
        String productName = (String) payload.get("productName");
        Integer quantity = (Integer) payload.get("quantity");
        if (productName == null || quantity == null || quantity <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "商品名称和数量不能为空且数量必须大于0"));
        }
        // 预检查库存
        int availableStock = stockService.getAvailableStock(userId);
        if (availableStock < quantity) {
            return ResponseEntity.status(400).body(Map.of("error", "库存不足，当前可用库存：" + availableStock));
        }
        try {
            // 发送事务消息，同步等待本地事务完成
            stockTxPublisher.sendCreateOrderTransaction(userId, productName, quantity);
            return ResponseEntity.ok(Map.of("message", "订单创建成功"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "下单失败: " + e.getMessage()));
        }
    }

    /**
     * 取消指定用户的指定订单（读写）
     * 需要 SCOPE_write 权限
     * Version 10 变更：改为仅取消订单 + 发送事务消息回补库存
     */
    @PostMapping("/users/{userId}/orders/{orderId}/cancel")
    @PreAuthorize("hasAuthority('SCOPE_write')")
    public ResponseEntity<?> cancelOrderForUser(@PathVariable Long userId,
                                                @PathVariable Long orderId,
                                                @AuthenticationPrincipal ClientPrincipal client) {
        try {
            stockTxPublisher.sendCancelOrderTransaction(orderId, userId);
            return ResponseEntity.ok(Map.of("message", "订单已取消"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "取消失败: " + e.getMessage()));
        }
    }
}
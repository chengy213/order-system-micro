package com.example.ordersystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部客户端配置属性，从 application.properties 中加载
 * 配置前缀: client.clients
 * 示例:
 *   client.clients.client1.secret=secret1
 *   client.clients.client1.scopes[0]=read
 *   client.clients.client1.scopes[1]=write
 */
@Component
@ConfigurationProperties(prefix = "client")
public class ClientProperties {

    // 存储多个客户端，key 为 clientId
    private Map<String, Client> clients = new HashMap<>();

    public Map<String, Client> getClients() {
        return clients;
    }

    public void setClients(Map<String, Client> clients) {
        this.clients = clients;
    }

    /**
     * 单个客户端配置
     */
    public static class Client {
        private String secret;          // 客户端密钥
        private List<String> scopes;    // 权限范围列表，如 ["read","write"]

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public List<String> getScopes() {
            return scopes;
        }

        public void setScopes(List<String> scopes) {
            this.scopes = scopes;
        }
    }
}
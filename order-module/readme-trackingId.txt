Topic: 如何实现全链路追踪功能？

一、实现思路
过滤器优先从请求头或Cookie中获取trackingId，若无则生成并设置到Cookie（HttpOnly? 如果设置为HttpOnly，前端无法读取，但没关系，浏览器自动携带）。
同时仍然保留响应头X-Tracking-Id供外部调用方使用。这样前端完全无需改动，所有页面跳转和表单提交都会自动携带Cookie中的trackingId，
从而实现同一用户会话内trackingId不变。我们提供修改后的TrackingIdFilter代码，并说明无需修改HTML。

- 添加依赖（logstash-logback-encoder，雪花算法库或自己实现）。
- 实现雪花算法ID生成器（SnowflakeIdWorker），直接使用hutool的现成组件。
- 实现一个过滤器（TrackingIdFilter），从请求头中获取或生成trackingId，并放入MDC，同时添加进请求Header（响应头）和Cookie
- 配置logback-spring.xml，使用LogstashEncoder，输出包含trackingId的JSON格式日志。
- 确保业务代码中（Controller, Service）可以方便地获取trackingId（可选）。

注意：如果未来跨服务调用，需要在HTTP请求头中传递X-Tracking-Id。

二、下面为您的 JWT 版本项目添加 ELK 日志收集 + 雪花算法全局 TrackingId 的完整实现。

2.1、添加 Maven 依赖（修改 pom.xml）
    - 在 <dependencies> 中加入以下内容：
    <!-- Logstash 日志编码器（输出 JSON 格式） -->
    <dependency>
        <groupId>net.logstash.logback</groupId>
        <artifactId>logstash-logback-encoder</artifactId>
        <version>7.4</version>
    </dependency>

    <!-- 用于方便生成雪花ID -->
    <dependency>
        <groupId>cn.hutool</groupId>
        <artifactId>hutool-all</artifactId>
        <version>5.8.35</version>
    </dependency>
2.2、实现 TrackingId 过滤器（放入 MDC）
具体请查看TrackingIdFilter.java

2.3、配置 Logback 输出 JSON 格式日志
具体请查看src/main/resources/logback-spring.xml文件配置

2.4、在 SecurityConfig 中注入并添加过滤器
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http...
            // 最先执行，保证trackingIdFilter在jwtAuthenticationFilter之前执行，这样一定会有一个trackingId被注入
            .addFilterBefore(trackingIdFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

2.5、跨服务调用时传递 trackingId
当您将来拆分微服务时，在调用下游服务的 HTTP 请求头中添加上游的 X-Tracking-Id：

示例：使用 RestTemplate 或 WebClient
// 获取当前请求的 trackingId
String trackingId = MDC.get("trackingId");
HttpHeaders headers = new HttpHeaders();
headers.set("X-Tracking-Id", trackingId);
HttpEntity<Void> entity = new HttpEntity<>(headers);
restTemplate.exchange("http://other-service/api/xxx", HttpMethod.GET, entity, String.class);
下游服务的 TrackingIdFilter 会从请求头中读取该值，从而实现全链路追踪。

三、如何验证？

3.1 前端浏览器中打开开发者工具，查看请求header，会始终有一个
x-tracking-id 2041714898077093888

且请求不同的业务接口，该x-tracking-id的值始终不变！(用户重新登录后可以重新生成!!!)

3.2 查看order-system.log的日志输出
{"@timestamp":"2026-04-08T11:19:40.487075+08:00","@version":"1","message":"Secured GET /orders",
"logger_name":"org.springframework.security.web.FilterChainProxy","thread_name":"http-nio-8080-exec-4",
"level":"DEBUG","level_value":10000,"trackingId":"2041714898077093888","app":"order-system"}
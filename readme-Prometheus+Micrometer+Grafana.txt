Prometheus + Micrometer + Grafana 底层原理及业务监控全链路
一、整体架构与数据流
业务应用 (order-module)
  └── Micrometer (指标库)
       ├── Counter (计数器)
       ├── Timer (计时器)
       └── DistributionSummary (分布摘要)
            ↓
 暴露 HTTP 端点 /actuator/prometheus (Prometheus 文本格式)
            ↓
Prometheus 服务器 (定期拉取 /pull)
  ├── 存储时间序列数据 (TSDB)
  ├── 通过 PromQL 查询聚合
  └── 根据告警规则 (AlertManager) 推送通知
            ↓
Grafana (数据源 = Prometheus)
  ├── 可视化仪表盘
  └── 配置告警 (也可直接使用 AlertManager)

二、核心组件原理
1. Micrometer – 应用内指标收集门面
- 作用：为 Java 应用提供统一的度量 API，屏蔽底层监控系统差异。
- 核心抽象：
    - Meter：所有指标的基本接口，包含 Meter.Id（名称、标签）。
    - Counter：只增不减的计数器，适用于请求次数、错误次数等。
    - Timer：记录持续时间，统计总时间、次数、百分位数。
    - Gauge：任意瞬时值，如当前活跃连接数。
- 注册表：MeterRegistry（如 PrometheusMeterRegistry）负责将指标暴露为 Prometheus 文本格式。
- 自动装配：Spring Boot 的 micrometer-registry-prometheus 会自动配置 MeterRegistry，并将 Spring MVC 请求、JVM 内存等内置指标注册进去。

2. Prometheus – 拉取式时序数据库
- 数据模型：每条时间序列由指标名称（metric name）和一组标签（key=value）唯一标识。
- 存储：本地磁盘，按 2 小时块存储，支持压缩和降采样。
- 拉取模式：通过 HTTP 定期从 /metrics 端点抓取数据，避免应用主动推送。
- 查询语言 PromQL：提供 rate()、increase()、histogram_quantile() 等函数，用于计算速率、滑动窗口、分位数。
- 告警：AlertManager 组件根据 PromQL 表达式触发告警，并通过路由发送到接收器（钉钉、邮件、Webhook）。

3. Grafana – 可视化与告警面板
- 数据源：连接到 Prometheus，使用 PromQL 查询时序数据。
- 仪表盘：通过面板（Graph、Stat、Table）展示指标变化。
- 告警：Grafana 内置告警引擎，可直接基于查询结果触发通知。

三、订单系统业务指标的全链路实现
步骤 1：定义业务指标注解和 AOP 切面（低侵入）
// 自定义注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BusinessMetric {
    String operation();
}

// AOP 切面
@Aspect
@Component
public class BusinessMetricAspect {
    private final MeterRegistry registry;

    @Around("@annotation(businessMetric)")
    public Object collect(ProceedingJoinPoint pjp, BusinessMetric businessMetric) throws Throwable {
        String operation = businessMetric.operation();
        Counter successCounter = Counter.builder("business.operation")
                .tag("operation", operation)
                .tag("result", "success")
                .register(registry);

步骤 2：业务代码添加注解
@PostMapping("/order/create")
@BusinessMetric(operation = "createOrder")
public String createOrder(...) { ... }

步骤 3：Micrometer 暴露指标
PrometheusMeterRegistry 自动将上述 Counter、Timer 转换为 Prometheus 文本格式，例如：

# HELP business_operation_total
# TYPE business_operation_total counter
business_operation_total{application="order-system",operation="cancelOrder",result="success"} 4.0
business_operation_total{application="order-system",operation="createOrder",result="success"} 5.0
business_operation_total{application="order-system",operation="login",result="success"} 2.0
business_operation_total{application="order-system",operation="orders",result="success"} 13.0
business_operation_total{application="order-system",operation="payCheck",result="success"} 5.0
# HELP business_operation_duration_seconds
# TYPE business_operation_duration_seconds summary
business_operation_duration_seconds_count{application="order-system",operation="cancelOrder",result="success"} 4
business_operation_duration_seconds_sum{application="order-system",operation="cancelOrder",result="success"} 0.307
business_operation_duration_seconds_count{application="order-system",operation="createOrder",result="success"} 5
business_operation_duration_seconds_sum{application="order-system",operation="createOrder",result="success"} 7.766
business_operation_duration_seconds_count{application="order-system",operation="login",result="success"} 2
business_operation_duration_seconds_sum{application="order-system",operation="login",result="success"} 2.258
business_operation_duration_seconds_count{application="order-system",operation="orders",result="success"} 13
business_operation_duration_seconds_sum{application="order-system",operation="orders",result="success"} 0.632
business_operation_duration_seconds_count{application="order-system",operation="payCheck",result="success"} 5
business_operation_duration_seconds_sum{application="order-system",operation="payCheck",result="success"} 6.804

通过 Spring Boot Actuator 端点 /actuator/prometheus 暴露，请确保通过http://localhost:8082/actuator/prometheus访问时能获取到数据。

步骤 4：Prometheus（9090端口） 拉取数据
配置文件 prometheus.yml 添加 job：
scrape_configs:
  - job_name: 'order-system'
    scrape_interval: 15s
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['order-module:8082']
Prometheus 每隔 15 秒发起 HTTP GET 请求，拉取所有指标并存入本地 TSDB。
请确保以下两个URL能访问到数据：
http://localhost:9090/metrics
http://localhost:9090/targets

步骤 5：业务监控指标计算（PromQL）
业务需求	                            PromQL 查询	                                                        说明
最近15分钟下单总数（滑动窗口）	        increase(business_operation_total{operation="createOrder"}[15m])	计算过去15分钟内的增量
订单成功率	        sum(rate(business_operation_total{operation="createOrder",result="success"}[5m])) / sum(rate(business_operation_total{operation="createOrder"}[5m]))	5分钟成功率
支付检查失败率	    sum(rate(business_operation_total{operation="payCheck",result="failure"}[15m])) / sum(rate(business_operation_total{operation="payCheck"}[15m]))
库存不足报警	        increase(business_stock_insufficient_total[5m]) > 10	5分钟内超过10次
用户活跃数	        sum(rate(business_operation_total{operation=~"login|createOrder"}[15m]))	粗略活跃事件速率

步骤 6：Grafana 可视化
- 添加 Prometheus 数据源。
- 创建面板，使用上述 PromQL 查询，选择图表类型（折线图、单值图）。
- 设置时间范围（最近 15 分钟、小时、天），自动刷新。

步骤 7：告警配置
方式一：Grafana 内置告警

在面板上配置告警规则，如“最近5分钟订单失败率 > 10%”，通知到钉钉/企业微信。

方式二：Prometheus AlertManager

在 rules.yml 定义告警规则：
groups:
- name: order_alerts
  rules:
  - alert: HighOrderFailureRate
    expr: (sum(rate(business_operation_total{operation="createOrder",result="failure"}[5m])) / sum(rate(business_operation_total{operation="createOrder"}[5m]))) > 0.1
    for: 2m
    labels:
      severity: warning
    annotations:
      summary: "订单失败率过高"

- AlertManager 接收告警后，通过 webhook 发送到钉钉机器人。

步骤 8：业务上下文的可观测性
- 在订单创建、支付检查失败时，AOP 会记录 MDC（failure_reason）并输出结构化日志（Logback JSON）。
- 日志通过 Filebeat/Logstash 送到 Elasticsearch，可在 Kibana 中按 trackingId、operation、failure_reason 检索，定位具体失败订单的入参和堆栈。
- 配合 Prometheus 指标，实现“指标监控 + 日志排查”的完整可观测性。

四、滑动窗口的实现原理
- Prometheus 的 rate() 函数基于 时间区间向量选择器（如 [15m]）计算每秒平均增长率，天然支持滑动窗口。
- 例如 increase(metric[15m]) 返回过去15分钟内的增量，无论 Prometheus 抓取间隔是多少，都会根据实际时间戳计算。
- 无需应用侧维护滑动窗口。

五、优势总结
- 低侵入：通过注解 + AOP 收集业务指标，业务代码几乎无感知。
- 统一抽象：Micrometer 支持多种监控系统，切换成本低。
- 水平扩展：Prometheus 拉取模式适合微服务，多实例指标自动聚合。
- 丰富查询：PromQL 提供强大的聚合、过滤、滑动窗口能力。
- 完整链路：从指标采集、存储、可视化到告警，全部开源且成熟。
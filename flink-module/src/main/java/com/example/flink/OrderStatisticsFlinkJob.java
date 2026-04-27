package com.example.flink;

import com.example.flink.model.FlinkOrderOperationEvent;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

/**
 * Flink 实时计算作业：
 * - 消费 Kafka topic "order-operations" 中的订单操作事件。
 * - 分别统计 CREATE_ORDER 和 CANCEL_ORDER 的数量。
 * - 使用滑动窗口：
 *     1) 15分钟窗口，步长1分钟（输出最近15分钟的累计值，每1分钟更新一次）
 *     2) 1小时窗口，步长3分钟（输出最近1小时的累计值，每3分钟更新一次）
 * - 结果写入 Redis，供订单系统查询接口读取。
 *
 * 注意：该作业独立于 Spring Boot 应用运行，通过 Flink 集群提交。
 *      开关控制仅影响查询接口，不影响作业本身。
 */
public class OrderStatisticsFlinkJob {

    public static void main(String[] args) throws Exception {
        // 1. 创建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 设置并行度，建议与 Kafka 分区数一致，这里为演示设为 1
        env.setParallelism(1);

        // 2. 配置 Kafka 数据源（使用新版 KafkaSource API）
        String bootstrapServers = "localhost:9092";
        String topic = "order-operations";
        String groupId = "flink-order-stats-group";

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(topic)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> rawStream = env.fromSource(kafkaSource,
                WatermarkStrategy.noWatermarks(),
                "Kafka Source");

        // 3. 将 JSON 字符串解析为 OrderOperationEvent 对象
        DataStream<FlinkOrderOperationEvent> eventStream = rawStream
                .map(new JsonToEventMapFunction())
                .name("Deserialize to OrderOperationEvent");

        // 提取 (operation, 1)
        DataStream<Tuple2<String, Long>> countStream = eventStream
                .map(event -> Tuple2.of(event.getOperationLog().getOperation(), 1L))
                .returns(Types.TUPLE(Types.STRING, Types.LONG))
                .name("Extract operation");

        // 分别过滤 CREATE 和 CANCEL
        DataStream<Tuple2<String, Long>> createStream = countStream
                .filter(tuple -> "CREATE_ORDER".equals(tuple.f0))
                .returns(Types.TUPLE(Types.STRING, Types.LONG));
        DataStream<Tuple2<String, Long>> cancelStream = countStream
                .filter(tuple -> "CANCEL_ORDER".equals(tuple.f0))
                .returns(Types.TUPLE(Types.STRING, Types.LONG));

        DataStream<Tuple2<String, Long>> merged = createStream.union(cancelStream);

        // 15分钟滑动窗口
        merged.keyBy(t -> t.f0)
                //每1分钟刷新一次数据
                .window(SlidingProcessingTimeWindows.of(Time.minutes(15), Time.minutes(1)))
                .sum(1)
                .addSink(new RedisWindowSink("15min"))
                .name("15min window sink");

        // 1小时滑动窗口
        merged.keyBy(t -> t.f0)
                //每3分钟刷新一次数据
                .window(SlidingProcessingTimeWindows.of(Time.hours(1), Time.minutes(3)))
                .sum(1)
                .addSink(new RedisWindowSink("1hour"))
                .name("1hour window sink");

        env.execute("Order Statistics Job");
        System.out.println("Order Statistics Job 已启动");
    }
}
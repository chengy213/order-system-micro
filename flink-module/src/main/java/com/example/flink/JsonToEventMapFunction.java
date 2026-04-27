package com.example.flink;

import com.example.flink.model.FlinkOrderOperationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.functions.MapFunction;

/**
 * 将 Kafka 中的 JSON 字符串转换为 FlinkOrderOperationEvent 对象。
 */
public class JsonToEventMapFunction implements MapFunction<String, FlinkOrderOperationEvent> {
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public FlinkOrderOperationEvent map(String value) throws Exception {
        return mapper.readValue(value, FlinkOrderOperationEvent.class);
    }
}
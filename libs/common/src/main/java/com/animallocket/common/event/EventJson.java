package com.animallocket.common.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * ObjectMapper riêng cho event: thời gian dạng ISO-8601, bỏ qua trường lạ để producer thêm trường mới
 * mà consumer cũ vẫn đọc được.
 */
public final class EventJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private EventJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String write(EventEnvelope envelope) {
        try {
            return MAPPER.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Không serialize được event " + envelope.type(), e);
        }
    }

    /** JSON hỏng ném IllegalArgumentException: lỗi không thử lại được. */
    public static EventEnvelope read(String json) {
        try {
            return MAPPER.readValue(json, EventEnvelope.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Event JSON không hợp lệ", e);
        }
    }
}

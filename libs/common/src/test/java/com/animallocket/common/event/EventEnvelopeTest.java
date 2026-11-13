package com.animallocket.common.event;

import com.animallocket.common.EventTypes;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventEnvelopeTest {

    record Sample(String publicId, int count, Instant at) {
    }

    @Test
    void ofFillsMetadata() {
        var a = EventEnvelope.of(EventTypes.USER_DELETED, "user-service", "k1", new Sample("p", 1, null));
        var b = EventEnvelope.of(EventTypes.USER_DELETED, "user-service", "k1", new Sample("p", 1, null));
        assertNotEquals(a.eventId(), b.eventId());
        assertEquals(1, a.version());
        assertEquals("user-service", a.producer());
        assertEquals("k1", a.key());
        assertFalse(a.occurredAt().isAfter(Instant.now()));
    }

    @Test
    void roundTripKeepsEnvelopeAndPayload() {
        Instant at = Instant.parse("2026-11-13T10:15:30.123Z");
        var env = EventEnvelope.of(EventTypes.USER_DELETED, "user-service", "k1", new Sample("abc", 3, at));
        String json = EventJson.write(env);
        assertTrue(json.contains("\"2026-11-13T10:15:30.123Z\""), "thời gian phải ở dạng ISO-8601: " + json);

        var back = EventJson.read(json);
        assertEquals(env.eventId(), back.eventId());
        assertEquals(env.type(), back.type());
        assertEquals(env.occurredAt(), back.occurredAt());
        assertEquals(new Sample("abc", 3, at), back.payloadAs(Sample.class));
    }

    @Test
    void unknownFieldsAreIgnoredForForwardCompatibility() {
        String json = """
                {"eventId":"e1","type":"X","version":2,"producer":"p","key":"k","newField":true,
                 "payload":{"publicId":"abc","count":1,"extra":"ignored"}}""";
        var env = EventJson.read(json);
        assertEquals("abc", env.payloadAs(Sample.class).publicId());
    }

    @Test
    void malformedJsonIsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> EventJson.read("{not json"));
    }

    @Test
    void wrongPayloadShapeIsIllegalArgument() {
        var env = EventJson.read("{\"eventId\":\"e1\",\"type\":\"X\",\"payload\":{\"count\":\"many\"}}");
        assertThrows(IllegalArgumentException.class, () -> env.payloadAs(Sample.class));
    }
}

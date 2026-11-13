package com.animallocket.common.event;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Mọi payload trong {@link Events} phải đi qua envelope JSON và quay lại nguyên vẹn. */
class EventsContractTest {

    private static List<Class<?>> payloadTypes() {
        return Arrays.stream(Events.class.getDeclaredClasses()).filter(Class::isRecord).toList();
    }

    private static Object sample(Class<?> type, int seed) throws Exception {
        RecordComponent[] parts = type.getRecordComponents();
        Object[] args = new Object[parts.length];
        Class<?>[] types = new Class<?>[parts.length];
        for (int i = 0; i < parts.length; i++) {
            Class<?> t = parts[i].getType();
            types[i] = t;
            if (t == String.class) {
                args[i] = parts[i].getName() + "-" + seed;
            } else if (t == int.class) {
                args[i] = seed + i;
            } else if (t == Instant.class) {
                args[i] = Instant.parse("2026-11-13T00:00:00Z").plusSeconds(seed);
            } else {
                throw new AssertionError("Kiểu chưa hỗ trợ trong test: " + t + " ở " + type.getSimpleName());
            }
        }
        Constructor<?> ctor = type.getDeclaredConstructor(types);
        return ctor.newInstance(args);
    }

    @Test
    void everyPayloadRoundTripsThroughTheEnvelope() throws Exception {
        List<Class<?>> types = payloadTypes();
        assertEquals(17, types.size(), "thêm/xoá payload thì cập nhật số lượng ở đây");
        int seed = 1;
        for (Class<?> type : types) {
            Object payload = sample(type, seed++);
            var env = EventEnvelope.of(type.getSimpleName(), "test", "key", payload);
            var back = EventJson.read(EventJson.write(env));
            assertEquals(payload, back.payloadAs(type), type.getSimpleName());
        }
    }

    @Test
    void payloadsCarryNoPersonalData() {
        List<String> banned = List.of("email", "phone", "sub", "birth", "latitude", "longitude", "name");
        for (Class<?> type : payloadTypes()) {
            for (RecordComponent c : type.getRecordComponents()) {
                String n = c.getName().toLowerCase(Locale.ROOT);
                for (String b : banned) {
                    assertFalse(n.equals(b) || n.startsWith(b), type.getSimpleName() + "." + c.getName());
                }
            }
        }
    }
}

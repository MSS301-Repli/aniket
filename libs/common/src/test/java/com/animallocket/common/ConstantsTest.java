package com.animallocket.common;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstantsTest {

    private static List<Field> constants(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == String.class)
                .toList();
    }

    @Test
    void eventTypeValuesEqualTheirNames() throws Exception {
        for (Field f : constants(EventTypes.class)) {
            assertEquals(f.getName(), f.get(null), "EventTypes." + f.getName());
        }
    }

    @Test
    void topicsAreUniqueAndNotDeadLetterTopics() throws Exception {
        Set<Object> seen = new HashSet<>();
        for (Field f : constants(Topics.class)) {
            String topic = (String) f.get(null);
            assertTrue(seen.add(topic), "trùng topic " + topic);
            assertTrue(topic.endsWith(".events"), topic);
        }
    }
}

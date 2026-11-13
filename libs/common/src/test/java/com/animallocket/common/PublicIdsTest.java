package com.animallocket.common;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicIdsTest {

    @Test
    void generatedIdsAreValidAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            String id = PublicIds.generate();
            assertEquals(PublicIds.LENGTH, id.length());
            assertTrue(PublicIds.isValid(id), id);
            assertTrue(seen.add(id), "trùng id " + id);
        }
    }

    @Test
    void generatorUsesTheWholeAlphabet() {
        Set<Character> chars = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            for (char c : PublicIds.generate().toCharArray()) {
                chars.add(c);
            }
        }
        assertEquals(32, chars.size());
    }

    @Test
    void rejectsMalformedIds() {
        assertFalse(PublicIds.isValid(null));
        assertFalse(PublicIds.isValid(""));
        assertFalse(PublicIds.isValid("short"));
        assertFalse(PublicIds.isValid("abcdefghijklmnopqrstu"));      // 21 ký tự
        assertFalse(PublicIds.isValid("UPPERCASEUPPERCASEAB"));       // chữ hoa
        assertFalse(PublicIds.isValid("0000000000000000000a"));       // 0, 1, 8, 9 không thuộc base32
        assertFalse(PublicIds.isValid("abcdefghijklmnopqrs1"));
        assertFalse(PublicIds.isValid("abcdefghij klmnopqrs"));
        assertFalse(PublicIds.isValid("abcdefghijklmnopqrst\n"));
        assertTrue(PublicIds.isValid("abcdefghijklmnopqrst"));
        assertTrue(PublicIds.isValid("2234567abcdefghijklm"));
    }
}

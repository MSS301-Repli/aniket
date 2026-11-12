package com.animallocket.user.service;

import com.animallocket.user.domain.Animal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalCatalogTest {

    @Test
    void emptyPoolGivesNothing() {
        assertTrue(AnimalCatalog.pick(List.of(), new Random(1)).isEmpty());
    }

    @Test
    void weightedPickFavoursCommonOverEpic() {
        List<Animal> pool = List.of(
                new Animal("c", "c", Animal.Rarity.COMMON, null),
                new Animal("e", "e", Animal.Rarity.EPIC, null));
        Random rnd = new Random(42);
        int common = 0;
        for (int i = 0; i < 10_000; i++) {
            if (AnimalCatalog.pick(pool, rnd).orElseThrow().getCode().equals("c")) {
                common++;
            }
        }
        // Kỳ vọng ~93% (70/75).
        assertTrue(common > 9_000 && common < 9_600, "common=" + common);
    }

    @Test
    void aliasPolicyAcceptsVietnameseAndRejectsBannedWords() {
        assertEquals("Mèo Con", AliasPolicy.validate("  Mèo   Con "));
        org.junit.jupiter.api.Assertions.assertThrows(com.animallocket.common.web.ApiException.class,
                () -> AliasPolicy.validate("ADMİN"));
        org.junit.jupiter.api.Assertions.assertThrows(com.animallocket.common.web.ApiException.class,
                () -> AliasPolicy.validate("ab"));
        org.junit.jupiter.api.Assertions.assertThrows(com.animallocket.common.web.ApiException.class,
                () -> AliasPolicy.validate("<script>"));
    }

    @Test
    void generatedAliasFitsLimit() {
        for (int i = 0; i < 200; i++) {
            String alias = AliasPolicy.generate("Chim cánh cụt");
            assertTrue(alias.length() >= 3 && alias.length() <= 20, alias);
        }
    }
}

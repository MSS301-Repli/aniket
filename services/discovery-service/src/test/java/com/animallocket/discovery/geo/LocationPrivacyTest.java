package com.animallocket.discovery.geo;

import com.animallocket.discovery.geo.LocationPrivacy.LatLon;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationPrivacyTest {
    final LocationPrivacy privacy = new LocationPrivacy("unit-test-secret-0123456789");
    final LatLon hanoi = new LatLon(21.028511, 105.804817);

    @Test
    void pointsInTheSameCellSnapToTheSameCenter() {
        LatLon a = LocationPrivacy.snap(hanoi);
        // dịch vài chục mét vẫn trong cùng ô (ô ~500 m)
        LatLon nearby = new LatLon(hanoi.lat() + 0.0001, hanoi.lon() + 0.0001);
        assertEquals(a, LocationPrivacy.snap(nearby));
        assertEquals(a, LocationPrivacy.snap(a)); // lũy đẳng
    }

    @Test
    void snappingNeverMovesMoreThanHalfADiagonalOfTheCell() {
        for (double dLat = -0.02; dLat <= 0.02; dLat += 0.0013) {
            for (double dLon = -0.02; dLon <= 0.02; dLon += 0.0017) {
                LatLon p = new LatLon(hanoi.lat() + dLat, hanoi.lon() + dLon);
                assertTrue(LocationPrivacy.distanceMeters(p, LocationPrivacy.snap(p)) <= 360,
                        "snap lệch quá xa tại " + p);
            }
        }
    }

    @Test
    void storedPositionStaysWithinBoundedErrorOfTruth() {
        for (int i = 0; i < 200; i++) {
            LatLon p = new LatLon(hanoi.lat() + i * 0.0007, hanoi.lon() - i * 0.0009);
            double err = LocationPrivacy.distanceMeters(p, privacy.storedPosition("user" + i, p));
            assertTrue(err <= 360 + 355, "sai số " + err);
        }
    }

    @Test
    void movingInsideACellDoesNotLeakFinerPosition() {
        // Chống trilateration: nhiều điểm trong cùng ô cho CÙNG vị trí lưu, nên không thu hẹp được.
        Set<LatLon> stored = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            stored.add(privacy.storedPosition("victim", new LatLon(hanoi.lat() + i * 0.00001, hanoi.lon() + i * 0.00001)));
        }
        assertEquals(1, stored.size());
    }

    @Test
    void offsetIsStablePerUserButDiffersAcrossUsers() {
        LatLon a1 = privacy.storedPosition("alice", hanoi);
        LatLon a2 = privacy.storedPosition("alice", hanoi);
        LatLon b = privacy.storedPosition("bob", hanoi);
        assertEquals(a1, a2);
        assertNotEquals(a1, b);
        for (String u : new String[]{"a", "b", "c", "d", "e"}) {
            double[] o = privacy.offsetMeters(u);
            assertTrue(Math.abs(o[0]) <= LocationPrivacy.MAX_OFFSET_METERS && Math.abs(o[1]) <= LocationPrivacy.MAX_OFFSET_METERS);
        }
    }

    @Test
    void differentSecretsGiveDifferentOffsets() {
        LocationPrivacy other = new LocationPrivacy("another-secret-0123456789");
        assertNotEquals(privacy.storedPosition("alice", hanoi), other.storedPosition("alice", hanoi));
    }

    @Test
    void bandsAreCoarseAndMinorsGetCoarserBands() {
        assertEquals("UNDER_1KM", LocationPrivacy.band(500, false));
        assertEquals("1_TO_3KM", LocationPrivacy.band(1500, false));
        assertEquals("3_TO_10KM", LocationPrivacy.band(5000, false));
        assertEquals("OVER_10KM", LocationPrivacy.band(50_000, false));
        // người dưới 18 không bao giờ nhận mức "dưới 1 km"
        for (double d : new double[]{0, 100, 900, 2999}) {
            assertEquals("UNDER_3KM", LocationPrivacy.band(d, true));
        }
    }

    @Test
    void rejectsWeakSecretAndInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new LocationPrivacy("short"));
        assertThrows(IllegalArgumentException.class, () -> new LatLon(91, 0));
        assertThrows(IllegalArgumentException.class, () -> new LatLon(0, 181));
        assertThrows(IllegalArgumentException.class, () -> new LatLon(Double.NaN, 0));
    }

    @Test
    void polesAndDatelineDoNotBreak() {
        LocationPrivacy.snap(new LatLon(90, 180));
        LocationPrivacy.snap(new LatLon(-90, -180));
        privacy.storedPosition("x", new LatLon(89.9999, 179.9999));
    }

    @Test
    void distanceIsReasonable() {
        LatLon hcm = new LatLon(10.7769, 106.7009);
        double km = LocationPrivacy.distanceMeters(hanoi, hcm) / 1000;
        assertTrue(km > 1100 && km < 1200, "Hà Nội–TP.HCM ≈ 1150 km, got " + km);
    }
}

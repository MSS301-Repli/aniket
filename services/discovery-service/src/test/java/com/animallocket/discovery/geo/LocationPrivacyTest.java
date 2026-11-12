package com.animallocket.discovery.geo;

import com.animallocket.discovery.geo.LocationPrivacy.LatLon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationPrivacyTest {
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
    void rejectsInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new LatLon(91, 0));
        assertThrows(IllegalArgumentException.class, () -> new LatLon(0, 181));
        assertThrows(IllegalArgumentException.class, () -> new LatLon(Double.NaN, 0));
    }

    @Test
    void polesAndDatelineDoNotBreak() {
        LocationPrivacy.snap(new LatLon(90, 180));
        LocationPrivacy.snap(new LatLon(-90, -180));
    }

    @Test
    void distanceIsReasonable() {
        LatLon hcm = new LatLon(10.7769, 106.7009);
        double km = LocationPrivacy.distanceMeters(hanoi, hcm) / 1000;
        assertTrue(km > 1100 && km < 1200, "Hà Nội–TP.HCM ≈ 1150 km, got " + km);
    }
}

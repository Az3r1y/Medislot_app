package fr.medislot.geo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BoundingBoxTest {
    @Test
    void boxContainsCircleButCornersFallOutside() {
        var box = BoundingBox.around(45.7576, 4.8317, 5);
        // Demi-largeur = demi-hauteur / cos(latitude).
        double halfHeight = box.maxLat() - 45.7576, halfWidth = box.maxLon() - 4.8317;
        assertThat(halfHeight)
                .isCloseTo(5 / (Math.PI * GeoDistance.EARTH_RADIUS_KM / 180), within(1e-12));
        assertThat(halfWidth * Math.cos(Math.toRadians(45.7576)))
                .isCloseTo(halfHeight, within(1e-12));
        assertThat(halfWidth).isGreaterThan(halfHeight);
        // 42 rue Jules Cambon : dans la boîte, mais à 5,4 km.
        assertThat(box.contains(45.7222, 4.8800)).isTrue();
        assertThat(GeoDistance.haversineKm(45.7576, 4.8317, 45.7222, 4.8800)).isGreaterThan(5);
    }

    @Test
    void nearPoleCoversAllLongitudes() {
        var box = BoundingBox.around(89.99, 10, 5);
        assertThat(box.maxLat()).isEqualTo(90);
        assertThat(box.minLon()).isEqualTo(-180);
        assertThat(box.maxLon()).isEqualTo(180);
    }

    @Test
    void boxWrapsAcrossAntimeridian() {
        var box = BoundingBox.around(-17.7, 179.99, 5); // Fidji
        assertThat(box.crossesAntimeridian()).isTrue();
        assertThat(box.contains(-17.7, -179.99)).isTrue();
        assertThat(box.contains(-17.7, 0)).isFalse();
    }
}

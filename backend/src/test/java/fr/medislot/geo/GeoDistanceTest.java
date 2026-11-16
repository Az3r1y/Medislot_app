package fr.medislot.geo;

import static fr.medislot.geo.GeoDistance.*;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

// Valeurs attendues publiées ou tirées d'une définition, pas recalculées avec ce code.
class GeoDistanceTest {
    static final double BELLECOUR_LAT = 45.7576, BELLECOUR_LON = 4.8317;

    @Test
    void nashvilleToLosAngelesMatchesPublishedValue() {
        // Valeur publiée par Rosetta Code pour R = 6372,8 km : 2887,2599506 km.
        double km = haversineKm(36.12, -86.67, 33.94, -118.40);
        assertThat(km * 6372.8 / EARTH_RADIUS_KM).isCloseTo(2887.2599506, within(1e-6));
    }

    @Test
    void oneDegreeOfLatitudeIsPiROver180() {
        assertThat(haversineKm(45, 4.8, 46, 4.8))
                .isCloseTo(Math.PI * EARTH_RADIUS_KM / 180, within(1e-9)); // 111,195 km
    }

    @Test
    void antipodesAreHalfCircumference() {
        assertThat(haversineKm(0, 0, 0, 180))
                .isCloseTo(Math.PI * EARTH_RADIUS_KM, within(1e-6)); // 20 015 km, sans NaN
    }

    @Test
    void samePointIsZeroAndDistanceIsSymmetric() {
        assertThat(haversineKm(BELLECOUR_LAT, BELLECOUR_LON, BELLECOUR_LAT, BELLECOUR_LON))
                .isZero();
        assertThat(haversineKm(BELLECOUR_LAT, BELLECOUR_LON, 48.8663, 2.3685))
                .isEqualTo(haversineKm(48.8663, 2.3685, BELLECOUR_LAT, BELLECOUR_LON));
    }

    @Test
    void lyonToParisIsAbout392Km() {
        // Bellecour -> Paris 11e : environ 392 km à vol d'oiseau.
        assertThat(haversineKm(BELLECOUR_LAT, BELLECOUR_LON, 48.8663, 2.3685))
                .isBetween(391.5, 393.0);
    }

    @Test
    void swappingLatAndLonGivesWrongAnswer() {
        // Un cabinet à 220 m de Bellecour, passé en (lon, lat) : on part dans le golfe de Guinée.
        double correct = haversineKm(BELLECOUR_LAT, BELLECOUR_LON, 45.7558, 4.8330);
        double swapped = haversineKm(BELLECOUR_LAT, BELLECOUR_LON, 4.8330, 45.7558);
        assertThat(correct).isCloseTo(0.22, within(0.01));
        assertThat(swapped).isGreaterThan(4000);
    }

    @Test
    void degreesWithoutConversionGiveAbsurdDistances() {
        // Oublier toRadians donne des distances absurdes entre cabinets voisins.
        double wrong =
                2
                        * EARTH_RADIUS_KM
                        * Math.asin(
                                Math.sqrt(
                                        Math.pow(Math.sin((45.7558 - BELLECOUR_LAT) / 2), 2)
                                                + Math.cos(BELLECOUR_LAT)
                                                        * Math.cos(45.7558)
                                                        * Math.pow(
                                                                Math.sin(
                                                                        (4.8330 - BELLECOUR_LON)
                                                                                / 2),
                                                                2)));
        assertThat(wrong)
                .isNotCloseTo(
                        haversineKm(BELLECOUR_LAT, BELLECOUR_LON, 45.7558, 4.8330), within(1.0));
    }

    @Test
    void roundsToTenthOfKilometre() {
        assertThat(roundKm(0.2249)).isEqualTo(0.2);
        assertThat(roundKm(1.25)).isEqualTo(1.3);
        assertThat(roundKm(3.849)).isEqualTo(3.8);
        assertThat(roundKm(0.04)).isEqualTo(0.0);
    }
}

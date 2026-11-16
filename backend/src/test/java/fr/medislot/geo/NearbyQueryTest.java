package fr.medislot.geo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class NearbyQueryTest {
    static void assertRejected(Runnable r, String message) {
        assertThatThrownBy(r::run)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(message)
                .satisfies(
                        e ->
                                assertThat(((ResponseStatusException) e).getStatusCode().value())
                                        .isEqualTo(400));
    }

    @Test
    void incompletePosition() {
        assertRejected(
                () -> NearbyQuery.of(45.7, null, null, "", 0, 6), NearbyQuery.POSITION_INCOMPLETE);
        assertRejected(
                () -> NearbyQuery.of(null, 4.8, null, "", 0, 6), NearbyQuery.POSITION_INCOMPLETE);
    }

    @Test
    void latitudeOutOfRangeNanAndInfinity() {
        for (double lat : new double[] {95, -90.01, Double.NaN, Double.POSITIVE_INFINITY})
            assertRejected(() -> NearbyQuery.of(lat, 4.8, null, "", 0, 6), NearbyQuery.LAT_OUT);
    }

    @Test
    void longitudeOutOfRange() {
        assertRejected(() -> NearbyQuery.of(45.7, 200.0, null, "", 0, 6), NearbyQuery.LON_OUT);
        assertRejected(() -> NearbyQuery.of(45.7, Double.NaN, null, "", 0, 6), NearbyQuery.LON_OUT);
    }

    @Test
    void radiusOutOfRange() {
        for (double r : new double[] {0, 0.05, 50.5, -5})
            assertRejected(() -> NearbyQuery.of(45.7, 4.8, r, "", 0, 6), NearbyQuery.RADIUS_OUT);
    }

    @Test
    void pagination() {
        assertRejected(() -> NearbyQuery.of(45.7, 4.8, null, "", -1, 6), NearbyQuery.PAGE_OUT);
        assertRejected(() -> NearbyQuery.of(45.7, 4.8, null, "", 0, 51), NearbyQuery.PAGE_OUT);
    }

    @Test
    void rejectionsComeInContractOrder() {
        assertRejected(() -> NearbyQuery.of(95.0, 200.0, 0.0, "", -1, 0), NearbyQuery.LAT_OUT);
    }

    @Test
    void defaultRadiusIs5KmAndBoundsAreInclusive() {
        assertThat(NearbyQuery.of(45.7576, 4.8317, null, null, 0, 6))
                .isEqualTo(new NearbyQuery(45.7576, 4.8317, 5, "", 0, 6));
        assertThatCode(() -> NearbyQuery.of(90.0, -180.0, 50.0, "", 0, 50))
                .doesNotThrowAnyException();
    }
}

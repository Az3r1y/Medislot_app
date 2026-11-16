package fr.medislot;

import static org.assertj.core.api.Assertions.*;

import fr.medislot.geo.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

// PostGIS doit rendre la même liste que la boîte englobante.
@SpringBootTest
@Testcontainers
class PostgisNearbyTest {
    @Container
    static PostgreSQLContainer db =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                                    System.getProperty(
                                            "postgis.image",
                                            "postgis/postgis:18-3.6@sha256:4a43e3c3e5d55b9208240fc71db543b79c638766280c625a425bd0a2390989c4"))
                            .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", db::getJdbcUrl);
        r.add("spring.datasource.username", db::getUsername);
        r.add("spring.datasource.password", db::getPassword);
        r.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/postgis");
        r.add("medislot.geo.finder", () -> "postgis");
    }

    @Autowired NearbyService service;
    @Autowired NearbyFinder finder;
    @Autowired JdbcTemplate jdbc;

    static List<String> names(NearbyService.Page p) {
        return p.items().stream().map(f -> f.professional().name).toList();
    }

    @Test
    void usesPostgisFinder() {
        assertThat(finder).isInstanceOf(PostgisNearbyFinder.class);
    }

    @Test
    void sameSixPractitionersInSameOrderAsBox() {
        var page = service.search(NearbyQuery.of(45.7576, 4.8317, null, "", 0, 20));
        assertThat(names(page)).containsExactlyElementsOf(NearbyPostgresTest.AROUND_BELLECOUR);
        // Ellipsoïde contre sphère : quelques mètres d'écart.
        page.items()
                .forEach(
                        f ->
                                assertThat(f.distanceKm())
                                        .isCloseTo(
                                                GeoDistance.haversineKm(
                                                        45.7576,
                                                        4.8317,
                                                        f.professional().latitude,
                                                        f.professional().longitude),
                                                within(0.02)));
    }

    @Test
    void locationColumnIsGeneratedAndIndexed() {
        assertThat(
                        jdbc.queryForObject(
                                "select is_generated from information_schema.columns where table_name='professional' and column_name='location'",
                                String.class))
                .isEqualTo("ALWAYS");
        assertThat(
                        jdbc.queryForObject(
                                "select indexdef from pg_indexes where indexname='professional_location_gix'",
                                String.class))
                .contains("USING gist");
    }
}

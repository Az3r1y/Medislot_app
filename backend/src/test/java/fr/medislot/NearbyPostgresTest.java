package fr.medislot;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.util.*;

// Recherche par position de bout en bout, sur les 11 praticiens de démonstration.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class NearbyPostgresTest {
    @Container
    static PostgreSQLContainer db =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                                    "postgres:18@sha256:2ea8630df176ad090ec92136dee850397c5af8781e4bcf2e4162593691c61bbc")
                            .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", db::getJdbcUrl);
        r.add("spring.flyway.url", db::getJdbcUrl);
        r.add("spring.flyway.user", db::getUsername);
        r.add("spring.flyway.password", db::getPassword);
        r.add("spring.datasource.username", () -> "medislot_app");
        r.add("spring.datasource.password", () -> "medislot-app-local-only");
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    static final List<String> AROUND_BELLECOUR =
            List.of(
                    "Dr Amel Benali",
                    "Dr Claire Fontaine",
                    "Dr Hélène Durand",
                    "Dr Hugo Petit",
                    "Dr Karim Haddad",
                    "Dr Julien Roux");

    record Reply(int status, JsonNode body) {}

    Reply get(String query) throws Exception {
        var r =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://localhost:"
                                                                + port
                                                                + "/api/professionals?"
                                                                + query))
                                        .header("X-Clinic", "cabinet-b")
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
        return new Reply(r.statusCode(), new ObjectMapper().readTree(r.body()));
    }

    static List<String> names(JsonNode b) {
        var l = new ArrayList<String>();
        b.get("items").forEach(i -> l.add(i.get("name").asString()));
        return l;
    }

    @Test
    void bellecourReturnsSixPractitionersNearestFirst() throws Exception {
        var r = get("lat=45.7576&lon=4.8317&size=20");
        assertThat(r.status()).isEqualTo(200);
        assertThat(names(r.body())).containsExactlyElementsOf(AROUND_BELLECOUR);
        assertThat(r.body().get("total").asInt()).isEqualTo(6);
        assertThat(r.body().get("items").get(0).get("distanceKm").asDouble()).isEqualTo(0.2);
        assertThat(r.body().get("items").get(5).get("distanceKm").asDouble()).isEqualTo(3.9);
    }

    @Test
    void filtersBySpecialty() throws Exception {
        assertThat(names(get("lat=45.7576&lon=4.8317&specialty=cardiology").body()))
                .containsExactly("Dr Hélène Durand", "Dr Julien Roux");
    }

    @Test
    void noPractitionerInGulfOfGuinea() throws Exception {
        assertThat(get("lat=0&lon=0").body().get("total").asInt()).isZero();
    }

    @Test
    void rejectsWith400AndAMessage() throws Exception {
        var abc = get("lat=abc&lon=4.8");
        assertThat(abc.status()).isEqualTo(400);
        assertThat(abc.body().get("detail").asString()).isEqualTo("Paramètre invalide : lat.");
        var outOfRange = get("lat=95&lon=4.8");
        assertThat(outOfRange.status()).isEqualTo(400);
        assertThat(outOfRange.body().get("detail").asString())
                .isEqualTo("Latitude hors bornes : lat doit être entre -90 et 90.");
    }

    @Test
    void searchWithoutPositionIsUnchanged() throws Exception {
        var r = get("size=50");
        assertThat(r.body().get("total").asInt()).isEqualTo(7);
        assertThat(r.body().get("items").get(0).get("distanceKm").isNull()).isTrue();
    }

    @Test
    void noTableStoresPatientPosition() {
        // Seule la table des praticiens a des colonnes de position.
        var tables =
                jdbc.queryForList(
                        "select distinct table_name from information_schema.columns where table_schema='public' and column_name ~* '(lat|lon|position|geo|location)'",
                        String.class);
        assertThat(tables).containsExactly("professional");
    }
}

package fr.medislot.tenant;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Console master : porte d'entrée séparée, CRUD des cabinets, vues minimisées. */
class PlatformConsoleTest extends PostgresTestBase {
    static final String MASTER = "master@medislot.test", PASSWORD = "MediSlot-demo-2026!";

    HttpClient master() throws Exception {
        var c = client();
        var response =
                call(
                        c,
                        "POST",
                        "/platform/login",
                        null,
                        "{\"email\":\"" + MASTER + "\",\"password\":\"" + PASSWORD + "\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("email").asString()).isEqualTo(MASTER);
        return c;
    }

    @Test
    void masterCannotUseTheCabinetDoor() throws Exception {
        var response =
                call(
                        client(),
                        "POST",
                        "/login",
                        null,
                        "username="
                                + URLEncoder.encode(MASTER, StandardCharsets.UTF_8)
                                + "&password=MediSlot-demo-2026%21");
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void cabinetAccountsAreRefusedOnThePlatform() throws Exception {
        var secretary = login("secretaire-a@medislot.test");
        var response = call(secretary, "GET", "/platform/clinics", null, null);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("réservée à l'équipe plateforme");
        assertThat(call(client(), "GET", "/platform/clinics", null, null).statusCode())
                .isEqualTo(401);
    }

    @Test
    void masterIsRefusedOnTheCabinetApi() throws Exception {
        var response = call(master(), "GET", "/appointments", "cabinet-a", null);
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void sameMessageAndLockAfterFiveFailures() throws Exception {
        String unknown = "inconnu-" + UUID.randomUUID() + "@medislot.test";
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 6; i++)
            statuses.add(
                    call(
                                    client(),
                                    "POST",
                                    "/platform/login",
                                    null,
                                    "{\"email\":\"" + unknown + "\",\"password\":\"mauvais-mot\"}")
                            .statusCode());
        assertThat(statuses).containsExactly(401, 401, 401, 401, 429, 429);
    }

    @Test
    void createUpdateAndDeleteAClinic() throws Exception {
        var master = master();
        String slug = "test-" + UUID.randomUUID().toString().substring(0, 8);
        String body =
                "{\"name\":\"Cabinet de test\",\"slug\":\""
                        + slug
                        + "\",\"city\":\"Lyon\",\"address\":\"12 rue Servient\","
                        + "\"color\":\"#0873B9\",\"adminEmail\":\"admin-"
                        + slug
                        + "@medislot.test\"}";
        var created = call(master, "POST", "/platform/clinics", null, body);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(json(created).get("temporaryPassword").asString()).hasSizeGreaterThan(12);
        assertThat(json(created).get("clinic").get("color").asString()).isEqualTo("#0873b9");

        var duplicate = call(master, "POST", "/platform/clinics", null, body);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(json(duplicate).get("errors").get(0).get("field").asString()).isEqualTo("slug");

        var updated =
                call(
                        master,
                        "PUT",
                        "/platform/clinics/" + slug,
                        null,
                        "{\"name\":\"Cabinet de test\",\"city\":\"Lyon\","
                                + "\"address\":\"12 rue Servient\",\"color\":\"#34b8b1\"}");
        assertThat(updated.statusCode()).isEqualTo(200);

        var audit = json(call(master, "GET", "/platform/clinics/" + slug + "/audit", null, null));
        assertThat(audit.get("items").toString()).contains("CLINIC_UPDATED").contains("Couleur");

        var list = call(master, "GET", "/platform/clinics?q=" + slug, null, null);
        assertThat(json(list).size()).isEqualTo(1);

        assertThat(
                        call(
                                        master,
                                        "DELETE",
                                        "/platform/clinics/" + slug,
                                        null,
                                        "{\"confirmSlug\":\"autre\",\"password\":\"" + PASSWORD + "\"}")
                                .statusCode())
                .isEqualTo(400);
        assertThat(
                        call(
                                        master,
                                        "DELETE",
                                        "/platform/clinics/" + slug,
                                        null,
                                        "{\"confirmSlug\":\"" + slug + "\",\"password\":\"faux\"}")
                                .statusCode())
                .isEqualTo(400);
        assertThat(
                        call(
                                        master,
                                        "DELETE",
                                        "/platform/clinics/" + slug,
                                        null,
                                        "{\"confirmSlug\":\""
                                                + slug
                                                + "\",\"password\":\""
                                                + PASSWORD
                                                + "\"}")
                                .statusCode())
                .isEqualTo(204);
        assertThat(call(master, "GET", "/platform/clinics/" + slug, null, null).statusCode())
                .isEqualTo(404);
        assertThat(json(call(master, "GET", "/platform/audit", null, null)).get("items").toString())
                .contains("CLINIC_DELETED");
    }

    @Test
    void appointmentsArePseudonymisedWithoutNameNorReason() throws Exception {
        long practitioner;
        LocalDate day;
        try (var connection = connection()) {
            connection.setAutoCommit(false);
            setClinic(connection, "1");
            try (var query =
                    connection.prepareStatement(
                            "select s.professional_id, s.starts_at from appointment a"
                                    + " join slot s on s.id=a.slot_id where a.id=?")) {
                query.setLong(1, appointmentA);
                var rs = query.executeQuery();
                assertThat(rs.next()).isTrue();
                practitioner = rs.getLong(1);
                day =
                        rs.getTimestamp(2)
                                .toInstant()
                                .atZone(ZoneId.of("Europe/Paris"))
                                .toLocalDate();
            }
            connection.rollback();
        }
        var response =
                call(
                        master(),
                        "GET",
                        "/platform/clinics/cabinet-a/practitioners/"
                                + practitioner
                                + "/appointments?week="
                                + day,
                        null,
                        null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("Camille").doesNotContain("patient@");
        var found = false;
        for (var item : json(response).get("appointments")) {
            List<String> fields = new ArrayList<>();
            for (var property : item.properties()) fields.add(property.getKey());
            assertThat(fields).containsExactlyInAnyOrder("id", "startsAt", "patient", "status");
            assertThat(item.get("patient").asString()).matches("P-[0-9A-F]{6}");
            found |= item.get("id").asLong() == appointmentA;
        }
        assertThat(found).isTrue();
        // Le praticien d'un autre cabinet est introuvable par ce cabinet.
        assertThat(
                        call(
                                        master(),
                                        "GET",
                                        "/platform/clinics/cabinet-b/practitioners/"
                                                + practitioner
                                                + "/appointments",
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(404);
    }

    @Test
    void statsAreCountsInParisTime() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Paris"));
        var stats =
                json(
                        call(
                                master(),
                                "GET",
                                "/platform/stats?from=" + today.minusDays(29) + "&to=" + today,
                                null,
                                null));
        assertThat(stats.get("timezone").asString()).isEqualTo("Europe/Paris");
        assertThat(stats.get("appointments").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(stats.get("perClinic").size()).isGreaterThanOrEqualTo(2);
        assertThat(stats.toString()).doesNotContain("@medislot.test");
    }
}

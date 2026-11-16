package fr.medislot.tenant;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.*;
import java.sql.SQLException;

class CalendarIsolationTest extends PostgresTestBase {
    String token(HttpClient client, String clinic) throws Exception {
        var response = call(client, "POST", "/calendar/subscription", clinic, "{}");
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response).get("token").asString();
    }

    HttpResponse<String> feed(String token, String clinic) throws Exception {
        return call(
                client(),
                "GET",
                "/calendar/feed.ics?clinic=" + clinic + "&token=" + token,
                null,
                null);
    }

    @Test
    void feedAndExportAreRestrictedToTheirClinic() throws Exception {
        var patient = login("patient@medislot.test");
        var a = token(patient, "cabinet-a");
        var b = token(patient, "cabinet-b");
        var feedA = feed(a, "cabinet-a");
        assertThat(feedA.statusCode()).isEqualTo(200);
        assertThat(feedA.body())
                .contains("UID:appointment-" + appointmentA + "@medislot")
                .doesNotContain("UID:appointment-" + appointmentB + "@medislot");
        assertThat(feed(b, "cabinet-b").body())
                .contains("UID:appointment-" + appointmentB + "@medislot");
        assertThat(feed(a, "cabinet-b").statusCode()).isEqualTo(404);
        assertThat(feed(b, "cabinet-a").statusCode()).isEqualTo(404);
        assertThat(
                        call(
                                        patient,
                                        "GET",
                                        "/appointments/" + appointmentA + "/calendar.ics",
                                        "cabinet-a",
                                        null)
                                .statusCode())
                .isEqualTo(200);
        assertThat(
                        call(
                                        patient,
                                        "GET",
                                        "/appointments/" + appointmentA + "/calendar.ics",
                                        "cabinet-b",
                                        null)
                                .statusCode())
                .isEqualTo(404);
    }

    @Test
    void rotationAndRevocationDoNotAffectOtherClinics() throws Exception {
        var patient = login("patient@medislot.test");
        var a = token(patient, "cabinet-a");
        var b = token(patient, "cabinet-b");
        var nextA = token(patient, "cabinet-a");
        assertThat(feed(a, "cabinet-a").statusCode()).isEqualTo(404);
        assertThat(feed(nextA, "cabinet-a").statusCode()).isEqualTo(200);
        assertThat(
                        call(patient, "DELETE", "/calendar/subscription", "cabinet-a", null)
                                .statusCode())
                .isEqualTo(204);
        assertThat(feed(nextA, "cabinet-a").statusCode()).isEqualTo(404);
        assertThat(feed(b, "cabinet-b").statusCode()).isEqualTo(200);
        assertThat(
                        json(call(patient, "GET", "/calendar/subscription", "cabinet-a", null))
                                .get("active")
                                .asBoolean())
                .isFalse();
        assertThat(
                        json(call(patient, "GET", "/calendar/subscription", "cabinet-b", null))
                                .get("active")
                                .asBoolean())
                .isTrue();
    }

    @Test
    void cancellationUpdatesSequenceAndConditionalFeed() throws Exception {
        var patient = login("patient@medislot.test");
        var token = token(patient, "cabinet-a");
        var first = feed(token, "cabinet-a");
        var etag = first.headers().firstValue("ETag").orElseThrow();
        var unchanged =
                client().send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://localhost:"
                                                                + port
                                                                + "/api/calendar/feed.ics?clinic=cabinet-a&token="
                                                                + token))
                                        .header("If-None-Match", etag)
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
        assertThat(unchanged.statusCode()).isEqualTo(304);
        assertThat(
                        call(
                                        patient,
                                        "POST",
                                        "/appointments/" + appointmentA + "/cancel",
                                        "cabinet-a",
                                        "{}")
                                .statusCode())
                .isEqualTo(200);
        var changed = feed(token, "cabinet-a");
        assertThat(changed.headers().firstValue("ETag").orElseThrow()).isNotEqualTo(etag);
        assertThat(changed.body()).contains("SEQUENCE:1", "STATUS:CANCELLED");
    }

    @Test
    void subscriptionTableAlsoHasRowLevelSecurity() throws Exception {
        token(login("patient@medislot.test"), "cabinet-a");
        try (var c = connection()) {
            c.setAutoCommit(false);
            try (var s = c.createStatement();
                    var rows = s.executeQuery("select count(*) from calendar_subscription")) {
                rows.next();
                assertThat(rows.getLong(1)).isZero();
            }
            setClinic(c, "1");
            try (var s = c.createStatement();
                    var rows =
                            s.executeQuery(
                                    "select count(*),count(*) filter(where clinic_id<>1) from calendar_subscription")) {
                rows.next();
                assertThat(rows.getLong(1)).isPositive();
                assertThat(rows.getLong(2)).isZero();
            }
            assertThatThrownBy(
                            () ->
                                    c.createStatement()
                                            .executeUpdate(
                                                    "update calendar_subscription set clinic_id=2"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(
                            e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("42501"));
            c.rollback();
        }
    }
}

package fr.medislot.tenant;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

class TenantIsolationTest extends PostgresTestBase {
    @Test
    void publicDirectoriesAreDisjoint() throws Exception {
        var c = client();
        var a = json(call(c, "GET", "/professionals?size=50", "cabinet-a", null));
        var b = json(call(c, "GET", "/professionals?size=50", "cabinet-b", null));
        assertThat(a.get("total").asInt()).isEqualTo(4);
        assertThat(b.get("total").asInt()).isEqualTo(7);
        var ids = new java.util.HashSet<Long>();
        a.get("items").forEach(p -> ids.add(p.get("id").asLong()));
        b.get("items").forEach(p -> assertThat(ids).doesNotContain(p.get("id").asLong()));
    }

    @Test
    void memberCanUseBothClinicsButReadOfOtherAppointmentIs404() throws Exception {
        var c = login("patient@medislot.test");
        assertThat(call(c, "GET", "/appointments/" + appointmentA, "cabinet-a", null).statusCode())
                .isEqualTo(200);
        assertThat(call(c, "GET", "/appointments/" + appointmentA, "cabinet-b", null).statusCode())
                .isEqualTo(404);
        json(call(c, "GET", "/appointments", "cabinet-b", null))
                .forEach(a -> assertThat(a.get("id").asLong()).isNotEqualTo(appointmentA));
    }

    @Test
    void nonMemberAndUnknownClinicHaveSame404() throws Exception {
        var c = login("autre@medislot.test");
        var denied = call(c, "GET", "/appointments", "cabinet-b", null);
        var unknown = call(c, "GET", "/appointments", "inconnu", null);
        assertThat(denied.statusCode()).isEqualTo(404);
        assertThat(denied.body()).isEqualTo(unknown.body());
    }

    @Test
    void secretarySeesOnlyTheirAgenda() throws Exception {
        var c = login("secretaire-a@medislot.test");
        var own = call(c, "GET", "/clinic/agenda", "cabinet-a", null);
        assertThat(own.statusCode()).isEqualTo(200);
        assertThat(own.body()).contains("\"id\":" + appointmentA);
        json(own).forEach(a -> assertThat(a.get("id").asLong()).isNotEqualTo(appointmentB));
        assertThat(call(c, "GET", "/clinic/agenda", "cabinet-b", null).statusCode()).isEqualTo(404);
        assertThat(
                        call(
                                        login("patient@medislot.test"),
                                        "GET",
                                        "/clinic/agenda",
                                        "cabinet-a",
                                        null)
                                .statusCode())
                .isEqualTo(403);
    }

    @Test
    void foreignMutationsHistoryAndPdfAre404() throws Exception {
        var c = login("patient@medislot.test");
        assertThat(
                        call(
                                        c,
                                        "POST",
                                        "/appointments/" + appointmentA + "/cancel",
                                        "cabinet-b",
                                        "{}")
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        call(
                                        c,
                                        "PATCH",
                                        "/appointments/" + appointmentA,
                                        "cabinet-b",
                                        "{\"slotId\":1}")
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        call(
                                        c,
                                        "GET",
                                        "/appointments/" + appointmentA + "/history",
                                        "cabinet-b",
                                        null)
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        call(
                                        c,
                                        "GET",
                                        "/appointments/" + appointmentA + "/convocation.pdf",
                                        "cabinet-b",
                                        null)
                                .statusCode())
                .isEqualTo(404);
    }

    @Test
    void registerCreatesMembershipOnlyInSelectedClinic() throws Exception {
        var email = "new-" + java.util.UUID.randomUUID() + "@medislot.test";
        var c = client();
        assertThat(
                        call(
                                        c,
                                        "POST",
                                        "/register",
                                        "cabinet-b",
                                        "{\"email\":\""
                                                + email
                                                + "\",\"name\":\"Test\",\"password\":\"MediSlot-demo-2026!\"}")
                                .statusCode())
                .isEqualTo(201);
        c = login(email);
        assertThat(call(c, "GET", "/appointments", "cabinet-b", null).statusCode()).isEqualTo(200);
        assertThat(call(c, "GET", "/appointments", "cabinet-a", null).statusCode()).isEqualTo(404);
    }

    @Test
    void honeypotFieldBlocksRegisterAndLogin() throws Exception {
        var c = client();
        assertThat(
                        call(
                                        c,
                                        "POST",
                                        "/register",
                                        "cabinet-a",
                                        "{\"email\":\"bot-"
                                                + java.util.UUID.randomUUID()
                                                + "@medislot.test\",\"name\":\"Bot\",\"password\":\"MediSlot-demo-2026!\",\"website\":\"http://spam\"}")
                                .statusCode())
                .isEqualTo(400);
        assertThat(
                        call(
                                        c,
                                        "POST",
                                        "/login",
                                        null,
                                        "username=patient%40medislot.test&password=MediSlot-demo-2026%21&website=spam")
                                .statusCode())
                .isEqualTo(401);
        login("patient@medislot.test");
    }

    @Test
    void registerDoesNotRevealExistingAccount() throws Exception {
        var fresh =
                call(
                        client(),
                        "POST",
                        "/register",
                        "cabinet-a",
                        "{\"email\":\"new-"
                                + java.util.UUID.randomUUID()
                                + "@medislot.test\",\"name\":\"Test\",\"password\":\"Autre-mot-de-passe-1\"}");
        var existing =
                call(
                        client(),
                        "POST",
                        "/register",
                        "cabinet-a",
                        "{\"email\":\"patient@medislot.test\",\"name\":\"Test\",\"password\":\"Autre-mot-de-passe-1\"}");
        assertThat(existing.statusCode()).isEqualTo(fresh.statusCode()).isEqualTo(201);
        assertThat(existing.body()).isEqualTo(fresh.body());
        // le compte existant garde son mot de passe
        login("patient@medislot.test");
    }

    @Test
    void compositeForeignKeyPreventsMixingClinicAndSlot() throws Exception {
        Long slot;
        try (var c = connection()) {
            c.setAutoCommit(false);
            setClinic(c, "2");
            try (var q = c.createStatement();
                    var r = q.executeQuery("select min(id) from slot")) {
                r.next();
                slot = r.getLong(1);
            }
            c.rollback();
            setClinic(c, "1");
            final long foreignSlot = slot;
            assertThatThrownBy(
                            () ->
                                    c.createStatement()
                                            .executeUpdate(
                                                    "insert into appointment(clinic_id,patient_id,slot_id,status,created_at,calendar_updated_at) values(1,(select id from app_user where email='patient@medislot.test'),"
                                                            + foreignSlot
                                                            + ",'CANCELLED',now(),now())"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(
                            e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23503"));
            c.rollback();
        }
    }

    @Test
    void headerIsRequiredAndCannotConflictWithSseParameter() throws Exception {
        assertThat(call(client(), "GET", "/professionals", null, null).statusCode()).isEqualTo(400);
        assertThat(
                        call(client(), "GET", "/professionals?clinic=cabinet-a", "cabinet-b", null)
                                .statusCode())
                .isEqualTo(400);
    }
}

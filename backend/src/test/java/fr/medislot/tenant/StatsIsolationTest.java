package fr.medislot.tenant;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StatsIsolationTest extends PostgresTestBase {
    @Test
    void registeringPatientInBMustNotChangeCountersInA() throws Exception {
        var member = login("patient@medislot.test");
        var beforeA = json(call(member, "GET", "/clinic/stats", "cabinet-a", null));
        var beforeB = json(call(member, "GET", "/clinic/stats", "cabinet-b", null));
        var email = "stats-" + java.util.UUID.randomUUID() + "@medislot.test";
        assertThat(
                        call(
                                        client(),
                                        "POST",
                                        "/register",
                                        "cabinet-b",
                                        "{\"email\":\""
                                                + email
                                                + "\",\"name\":\"Stats\",\"password\":\"MediSlot-demo-2026!\"}")
                                .statusCode())
                .isEqualTo(201);
        assertThat(json(call(member, "GET", "/clinic/stats", "cabinet-a", null)))
                .isEqualTo(beforeA);
        assertThat(
                        json(call(member, "GET", "/clinic/stats", "cabinet-b", null))
                                .get("patients")
                                .asLong())
                .isEqualTo(beforeB.get("patients").asLong() + 1);
    }
}

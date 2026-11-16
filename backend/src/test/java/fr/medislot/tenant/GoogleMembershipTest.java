package fr.medislot.tenant;

import static org.assertj.core.api.Assertions.*;

import fr.medislot.security.GoogleAccounts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GoogleMembershipTest extends PostgresTestBase {
    @Autowired GoogleAccounts accounts;

    @Test
    void firstLoginCreatesOnlyTheSelectedMembership() {
        TenantContext.set(2L);
        try {
            var user =
                    accounts.resolve(
                            "google-" + java.util.UUID.randomUUID(),
                            "google-" + java.util.UUID.randomUUID() + "@example.test",
                            true,
                            "Google Patient");
            assertThat(
                            jdbc.queryForList(
                                    "select clinic_id from membership where user_id=?",
                                    Long.class,
                                    user.id))
                    .containsExactly(2L);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void returningAccountDoesNotGainAccessToAnotherClinic() {
        String subject = "google-" + java.util.UUID.randomUUID();
        TenantContext.set(1L);
        try {
            var user =
                    accounts.resolve(
                            subject,
                            "google-" + java.util.UUID.randomUUID() + "@example.test",
                            true,
                            "Google Patient");
            TenantContext.set(2L);
            assertThat(accounts.resolve(subject, user.email, true, user.name).id)
                    .isEqualTo(user.id);
            assertThat(
                            jdbc.queryForList(
                                    "select clinic_id from membership where user_id=?",
                                    Long.class,
                                    user.id))
                    .containsExactly(1L);
        } finally {
            TenantContext.clear();
        }
    }
}

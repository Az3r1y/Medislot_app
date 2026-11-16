package fr.medislot.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.*;

class LoginAttemptsTest {
    static class MutableClock extends Clock {
        Instant t = Instant.parse("2026-11-16T10:00:00Z");

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId z) {
            return this;
        }

        public Instant instant() {
            return t;
        }
    }

    MutableClock h = new MutableClock();
    LoginAttempts a = new LoginAttempts(h);

    @Test
    void fiveFailuresLockForFifteenMinutes() {
        for (int i = 0; i < 4; i++) a.failed("patient@medislot.test");
        assertThat(a.locked("patient@medislot.test")).isFalse();
        a.failed("PATIENT@medislot.test "); // casse et espaces ignorés
        assertThat(a.locked("patient@medislot.test")).isTrue();
        h.t = h.t.plus(Duration.ofMinutes(14));
        assertThat(a.locked("patient@medislot.test")).isTrue();
        h.t = h.t.plus(Duration.ofMinutes(2));
        assertThat(a.locked("patient@medislot.test")).isFalse();
    }

    @Test
    void successResetsAndOldFailuresExpire() {
        for (int i = 0; i < 4; i++) a.failed("x@test");
        a.succeeded("x@test");
        a.failed("x@test");
        assertThat(a.locked("x@test")).isFalse();
        for (int i = 0; i < 4; i++) a.failed("y@test");
        h.t = h.t.plus(Duration.ofMinutes(16));
        a.failed("y@test");
        assertThat(a.locked("y@test")).as("les 4 premiers échecs sont hors fenêtre").isFalse();
    }

    @Test
    void lockedAccountDoesNotAffectOthers() {
        for (int i = 0; i < 5; i++) a.failed("cible@test");
        assertThat(a.locked("cible@test")).isTrue();
        assertThat(a.locked("autre@test")).isFalse();
    }
}

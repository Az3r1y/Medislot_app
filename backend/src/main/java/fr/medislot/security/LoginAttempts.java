package fr.medislot.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verrou par compte : 5 échecs en 15 minutes bloquent le compte 15 minutes, quelle que soit l'IP.
 * Toute adresse saisie est comptée, existante ou non, pour ne rien révéler. Compteur en mémoire : à
 * déplacer en base si l'API tourne sur plusieurs instances.
 */
@Component
public class LoginAttempts {
    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15), LOCK = Duration.ofMinutes(15);
    // Borne la mémoire face à des millions d'adresses inventées.
    static final int MAX_TRACKED = 50_000;

    private record State(int failures, Instant first, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttempts() {
        this(Clock.systemUTC());
    }

    LoginAttempts(Clock clock) {
        this.clock = clock;
    }

    private static String key(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    public boolean locked(String email) {
        State s = states.get(key(email));
        return s != null && s.lockedUntil() != null && clock.instant().isBefore(s.lockedUntil());
    }

    public void failed(String email) {
        Instant now = clock.instant();
        if (states.size() >= MAX_TRACKED)
            states.entrySet().removeIf(e -> expired(e.getValue(), now));
        states.compute(
                key(email),
                (k, s) -> {
                    if (s == null || expired(s, now)) s = new State(0, now, null);
                    int n = s.failures() + 1;
                    return new State(n, s.first(), n >= MAX_FAILURES ? now.plus(LOCK) : null);
                });
    }

    public void succeeded(String email) {
        states.remove(key(email));
    }

    private static boolean expired(State s, Instant now) {
        return s.lockedUntil() != null
                ? !now.isBefore(s.lockedUntil())
                : now.isAfter(s.first().plus(WINDOW));
    }
}

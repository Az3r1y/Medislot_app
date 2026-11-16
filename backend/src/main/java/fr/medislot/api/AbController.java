package fr.medislot.api;

import fr.medislot.application.AbService;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/ab")
public class AbController {
    public static final String COOKIE = "ab_visitor";
    private final AbService ab;
    private final boolean secureCookie;

    // Même règle que le cookie de session.
    public AbController(
            AbService ab,
            @org.springframework.beans.factory.annotation.Value(
                            "${server.servlet.session.cookie.secure:true}")
                    boolean secureCookie) {
        this.ab = ab;
        this.secureCookie = secureCookie;
    }

    @GetMapping("/assignment")
    public Map<String, Object> assignment(
            @CookieValue(name = COOKIE, required = false) String visitor, HttpServletResponse res) {
        if (!ab.enabled())
            return Map.of("enabled", false, "experiment", AbService.EXP, "variant", "A");
        UUID subject = subject(visitor);
        if (subject == null) {
            subject = UUID.randomUUID();
            res.addHeader(
                    HttpHeaders.SET_COOKIE,
                    ResponseCookie.from(COOKIE, subject.toString())
                            .httpOnly(true)
                            .secure(secureCookie)
                            .sameSite("Lax")
                            .path("/")
                            .maxAge(Duration.ofDays(90))
                            .build()
                            .toString());
        }
        var a = ab.assign(subject);
        return Map.of("enabled", true, "experiment", a.experiment(), "variant", a.variant());
    }

    @PostMapping("/exposure")
    public ResponseEntity<Void> exposure(
            @CookieValue(name = COOKIE, required = false) String visitor) {
        UUID s = subject(visitor);
        if (ab.enabled() && s != null) ab.expose(s);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return ab.metrics();
    }

    public static UUID subject(String cookie) {
        try {
            if (cookie == null) return null;
            UUID u = UUID.fromString(cookie);
            return u.toString().equals(cookie) ? u : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

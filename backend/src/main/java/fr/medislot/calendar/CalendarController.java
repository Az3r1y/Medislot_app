package fr.medislot.calendar;

import fr.medislot.application.BookingService;
import fr.medislot.repository.AppointmentRepository;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@RestController
@org.springframework.transaction.annotation.Transactional
@RequestMapping("/api")
public class CalendarController {
    private final BookingService booking;
    private final AppointmentRepository appointments;
    private final CalendarService calendar;
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public CalendarController(
            BookingService booking,
            AppointmentRepository appointments,
            CalendarService calendar,
            JdbcTemplate jdbc) {
        this.booking = booking;
        this.appointments = appointments;
        this.calendar = calendar;
        this.jdbc = jdbc;
    }

    private static String hash(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Calendrier introuvable.");
    }

    private static ResponseEntity.BodyBuilder headers() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff");
    }

    @GetMapping("/appointments/{id}/calendar.ics")
    public ResponseEntity<byte[]> appointment(Principal principal, @PathVariable Long id) {
        Long patient = booking.patient(principal.getName());
        var appointment =
                appointments
                        .findById(id)
                        .filter(a -> a.patientId.equals(patient))
                        .orElseThrow(CalendarController::missing);
        return headers()
                .headers(h -> h.setCacheControl("private, no-store"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rendez-vous.ics\"")
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .body(calendar.render(List.of(appointment)));
    }

    public record SubscriptionStatus(boolean active) {}

    public record SubscriptionToken(String token) {}

    @GetMapping("/calendar/subscription")
    public ResponseEntity<SubscriptionStatus> status(Principal principal) {
        boolean active =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "select exists(select 1 from calendar_subscription where user_id=?)",
                                Boolean.class,
                                booking.patient(principal.getName())));
        return headers()
                .headers(h -> h.setCacheControl("private, no-store"))
                .body(new SubscriptionStatus(active));
    }

    @PostMapping("/calendar/subscription")
    public ResponseEntity<SubscriptionToken> create(Principal principal) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update(
                """
                INSERT INTO calendar_subscription(token_hash,user_id,clinic_id)
                VALUES (?, ?, current_clinic())
                ON CONFLICT (user_id,clinic_id) DO UPDATE SET token_hash=excluded.token_hash
                """,
                hash(token.getBytes(StandardCharsets.US_ASCII)),
                booking.patient(principal.getName()));
        return headers()
                .headers(h -> h.setCacheControl("private, no-store"))
                .body(new SubscriptionToken(token));
    }

    @DeleteMapping("/calendar/subscription")
    public ResponseEntity<Void> revoke(Principal principal) {
        jdbc.update(
                "DELETE FROM calendar_subscription WHERE user_id = ?",
                booking.patient(principal.getName()));
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .build();
    }

    @GetMapping("/calendar/feed.ics")
    public ResponseEntity<byte[]> feed(
            @RequestParam(required = false) String token, WebRequest request) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw missing();
        var patients =
                jdbc.queryForList(
                        "select user_id from calendar_subscription where token_hash=?",
                        Long.class,
                        hash(token.getBytes(StandardCharsets.US_ASCII)));
        if (patients.isEmpty()) throw missing();
        byte[] body =
                calendar.render(
                        appointments.findByPatientIdOrderByCreatedAtDesc(patients.getFirst()));
        String etag = "\"" + hash(body) + "\"";
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                    .header("Referrer-Policy", "no-referrer")
                    .build();
        }
        return headers()
                .eTag(etag)
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .body(body);
    }
}

package fr.medislot.platform;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;

/**
 * Rendez-vous d'un praticien, vue minimisée : date, heure, statut et patient pseudonymisé. Le JSON
 * ne contient ni nom, ni identifiant de patient, ni motif.
 */
@RestController
@RequestMapping("/api/platform/clinics/{slug}/practitioners/{practitioner}/appointments")
public class PlatformAppointmentController {
    // CONFIRMED : à venir ; PAST : confirmé et passé ; CANCELLED : annulé.
    static final Set<String> STATUSES = Set.of("CONFIRMED", "PAST", "CANCELLED");

    private final JdbcTemplate jdbc;
    private final PlatformDb db;
    private final PlatformJournal journal;
    private final PlatformClinicController clinics;
    private final Pseudonyms pseudonyms;

    public PlatformAppointmentController(
            JdbcTemplate jdbc,
            PlatformDb db,
            PlatformJournal journal,
            PlatformClinicController clinics,
            Pseudonyms pseudonyms) {
        this.jdbc = jdbc;
        this.db = db;
        this.journal = journal;
        this.clinics = clinics;
        this.pseudonyms = pseudonyms;
    }

    public record Practitioner(long id, String name, String specialty) {}

    public record AppointmentRow(long id, Instant startsAt, String patient, String status) {}

    public record Week(
            Practitioner practitioner, LocalDate week, List<AppointmentRow> appointments) {}

    private record Raw(long id, Instant startsAt, long patientId, String status) {}

    @GetMapping
    public Week week(
            @PathVariable String slug,
            @PathVariable long practitioner,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate week,
            @RequestParam(required = false) String status,
            Principal master) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut inconnu.");
        long clinic = clinics.idOf(slug);
        LocalDate monday =
                (week == null ? LocalDate.now(PlatformStatsController.PARIS) : week)
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        OffsetDateTime start = monday.atStartOfDay(PlatformStatsController.PARIS).toOffsetDateTime();
        OffsetDateTime end = start.plusWeeks(1);
        Instant now = Instant.now();
        // Le cabinet passé dans l'URL est revérifié : la sécurité par ligne ne montre que ses
        // praticiens.
        return db.inClinic(
                clinic,
                () -> {
                    Practitioner who =
                            jdbc
                                    .query(
                                            "select id, name, specialty from professional where id=?",
                                            (rs, i) ->
                                                    new Practitioner(
                                                            rs.getLong(1),
                                                            rs.getString(2),
                                                            rs.getString(3)),
                                            practitioner)
                                    .stream()
                                    .findFirst()
                                    .orElseThrow(
                                            () ->
                                                    new ResponseStatusException(
                                                            HttpStatus.NOT_FOUND,
                                                            "Ce praticien n'existe pas dans ce cabinet."));
                    List<AppointmentRow> rows =
                            jdbc
                                    .query(
                                            """
                                    select a.id, s.starts_at, a.patient_id, a.status
                                    from appointment a join slot s on s.id=a.slot_id
                                    where s.professional_id=? and s.starts_at >= ? and s.starts_at < ?
                                    order by s.starts_at, a.id
                                    """,
                                            (rs, i) ->
                                                    new Raw(
                                                            rs.getLong(1),
                                                            rs.getTimestamp(2).toInstant(),
                                                            rs.getLong(3),
                                                            rs.getString(4)),
                                            practitioner,
                                            start,
                                            end)
                                    .stream()
                                    .map(
                                            r ->
                                                    new AppointmentRow(
                                                            r.id(),
                                                            r.startsAt(),
                                                            pseudonyms.of(clinic, r.patientId()),
                                                            statusOf(r, now)))
                                    .filter(
                                            r ->
                                                    status == null
                                                            || status.isBlank()
                                                            || r.status().equals(status))
                                    .toList();
                    journal.record(
                            master.getName(),
                            PlatformJournal.APPOINTMENTS_VIEWED,
                            clinic,
                            slug,
                            who.name());
                    return new Week(who, monday, rows);
                });
    }

    static String statusOf(Raw r, Instant now) {
        if ("CANCELLED".equals(r.status())) return "CANCELLED";
        return r.startsAt().isBefore(now) ? "PAST" : "CONFIRMED";
    }
}

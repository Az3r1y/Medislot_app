package fr.medislot.platform;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard global : uniquement des comptes, calculés côté back. Les jours sont comptés en heure de
 * Paris et la réponse le précise.
 */
@RestController
@RequestMapping("/api/platform/stats")
public class PlatformStatsController {
    public static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private final JdbcTemplate jdbc;
    private final PlatformDb db;
    private final PlatformClinicController clinics;

    public PlatformStatsController(
            JdbcTemplate jdbc, PlatformDb db, PlatformClinicController clinics) {
        this.jdbc = jdbc;
        this.db = db;
        this.clinics = clinics;
    }

    public record ClinicStat(String slug, String name, long professionals, long appointments) {}

    public record WeekStat(LocalDate start, LocalDate end, long appointments, boolean partial) {}

    public record Stats(
            String timezone,
            LocalDate from,
            LocalDate to,
            String clinic,
            long clinics,
            long professionals,
            long appointments,
            long cancelled,
            List<ClinicStat> perClinic,
            List<WeekStat> perWeek) {}

    @GetMapping
    public Stats stats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String clinic) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 366)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Période invalide : 366 jours au plus.");
        Long clinicId =
                clinic == null || clinic.isBlank() || clinic.equals("all")
                        ? null
                        : clinics.idOf(clinic);
        OffsetDateTime start = from.atStartOfDay(PARIS).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(PARIS).toOffsetDateTime();
        return db.acrossClinics(
                () -> {
                    long clinicCount =
                            clinicId != null
                                    ? 1
                                    : jdbc.queryForObject("select count(*) from clinic", Long.class);
                    long professionals =
                            jdbc.queryForObject(
                                    """
                            select count(*) from professional
                            where (cast(? as bigint) is null or clinic_id=?)
                            """,
                                    Long.class,
                                    clinicId,
                                    clinicId);
                    String appointmentFilter =
                            """
                            from appointment a
                            where a.created_at >= ? and a.created_at < ?
                              and (cast(? as bigint) is null or a.clinic_id=?)
                            """;
                    long appointments =
                            jdbc.queryForObject(
                                    "select count(*) " + appointmentFilter,
                                    Long.class,
                                    start,
                                    end,
                                    clinicId,
                                    clinicId);
                    long cancelled =
                            jdbc.queryForObject(
                                    "select count(*) " + appointmentFilter + " and a.status='CANCELLED'",
                                    Long.class,
                                    start,
                                    end,
                                    clinicId,
                                    clinicId);
                    List<ClinicStat> perClinic =
                            jdbc.query(
                                    """
                            select c.slug, c.name,
                              (select count(*) from professional p where p.clinic_id=c.id),
                              (select count(*) from appointment a where a.clinic_id=c.id
                                 and a.created_at >= ? and a.created_at < ?) as total
                            from clinic c
                            where (cast(? as bigint) is null or c.id=?)
                            order by total desc, c.name
                            """,
                                    (rs, i) ->
                                            new ClinicStat(
                                                    rs.getString(1),
                                                    rs.getString(2),
                                                    rs.getLong(3),
                                                    rs.getLong(4)),
                                    start,
                                    end,
                                    clinicId,
                                    clinicId);
                    Map<LocalDate, Long> counts = new HashMap<>();
                    jdbc.query(
                            """
                            select date_trunc('week', a.created_at at time zone 'Europe/Paris')::date,
                                   count(*)
                            """
                                    + appointmentFilter
                                    + " group by 1",
                            rs -> {
                                counts.put(rs.getObject(1, LocalDate.class), rs.getLong(2));
                            },
                            start,
                            end,
                            clinicId,
                            clinicId);
                    return new Stats(
                            PARIS.getId(),
                            from,
                            to,
                            clinicId == null ? "all" : clinic,
                            clinicCount,
                            professionals,
                            appointments,
                            cancelled,
                            perClinic,
                            weeks(from, to, counts));
                });
    }

    /** Une entrée par semaine (lundi à dimanche), semaines vides comprises. */
    static List<WeekStat> weeks(LocalDate from, LocalDate to, Map<LocalDate, Long> counts) {
        List<WeekStat> weeks = new ArrayList<>();
        for (LocalDate monday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                !monday.isAfter(to);
                monday = monday.plusWeeks(1)) {
            LocalDate sunday = monday.plusDays(6);
            weeks.add(
                    new WeekStat(
                            monday.isBefore(from) ? from : monday,
                            sunday.isAfter(to) ? to : sunday,
                            counts.getOrDefault(monday, 0L),
                            monday.isBefore(from) || sunday.isAfter(to)));
        }
        return weeks;
    }
}

package fr.medislot.platform;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Journal d'un cabinet vu par le master, et journal master. Le journal ne contient jamais de motif
 * de consultation ni de donnée de santé : seulement l'action et un identifiant d'objet. Les patients
 * y apparaissent sous pseudonyme. Consulter ou exporter le journal est journalisé.
 */
@RestController
@RequestMapping("/api/platform")
public class PlatformAuditController {
    static final int PAGE = 25, EXPORT_MAX = 10_000;
    private static final DateTimeFormatter CSV_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(PlatformStatsController.PARIS);

    private final JdbcTemplate jdbc;
    private final PlatformDb db;
    private final PlatformJournal journal;
    private final PlatformClinicController clinics;
    private final Pseudonyms pseudonyms;

    public PlatformAuditController(
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

    public record Entry(
            String id,
            Instant at,
            String actor,
            String role,
            String action,
            String target,
            boolean platform) {}

    public record Page(List<Entry> items, long total, String nextCursor) {}

    public record MasterEntry(
            long id, Instant at, String actor, String action, String clinic, String target) {}

    public record MasterPage(List<MasterEntry> items, Long nextCursor) {}

    private record Filters(
            String action, String role, OffsetDateTime from, OffsetDateTime to) {}

    private record Cursor(OffsetDateTime at, String uid) {}

    // audit_event est lu sous la politique du cabinet courant ; les actions du master sur ce
    // cabinet viennent de platform_audit et sont marquées comme telles.
    private static final String UNION =
            """
            from (
              select 'a-' || e.id as uid, e.created_at, u.id as actor_id, u.email, u.role,
                     e.action, 'RDV #' || e.appointment_id as target, false as platform
              from audit_event e join app_user u on u.id=e.actor_id
              union all
              select 'p-' || p.id, p.created_at, u.id, u.email, u.role,
                     p.action, p.target, true
              from platform_audit p join app_user u on u.id=p.actor_id
              where p.clinic_id=? and p.action <> 'AUDIT_VIEWED'
            ) j
            where (cast(? as text) is null or j.action=?)
              and (cast(? as text) is null or j.role=?)
              and (cast(? as timestamptz) is null or j.created_at >= ?)
              and (cast(? as timestamptz) is null or j.created_at < ?)
            """;

    @GetMapping("/clinics/{slug}/audit")
    public Page audit(
            @PathVariable String slug,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(required = false) String cursor,
            Principal master) {
        long id = clinics.idOf(slug);
        Filters filters = filters(action, role, from, to);
        Cursor after = cursor == null || cursor.isBlank() ? null : decode(cursor);
        return db.inClinic(
                id,
                () -> {
                    List<Object> args = new ArrayList<>(arguments(id, filters));
                    String sql =
                            "select j.uid, j.created_at, j.actor_id, j.email, j.role, j.action,"
                                    + " j.target, j.platform "
                                    + UNION;
                    if (after != null) {
                        sql += " and (j.created_at, j.uid) < (?, ?)";
                        args.add(after.at());
                        args.add(after.uid());
                    }
                    sql += " order by j.created_at desc, j.uid desc limit ?";
                    args.add(PAGE + 1);
                    List<Object[]> rows = jdbc.query(sql, (rs, i) -> raw(rs), args.toArray());
                    long total =
                            jdbc.queryForObject(
                                    "select count(*) " + UNION,
                                    Long.class,
                                    arguments(id, filters).toArray());
                    boolean more = rows.size() > PAGE;
                    List<Object[]> page = more ? rows.subList(0, PAGE) : rows;
                    if (after == null)
                        journal.record(
                                master.getName(), PlatformJournal.AUDIT_VIEWED, id, slug, null);
                    String next =
                            more
                                    ? encode((OffsetDateTime) page.getLast()[1], (String) page.getLast()[0])
                                    : null;
                    return new Page(page.stream().map(r -> entry(id, r)).toList(), total, next);
                });
    }

    @GetMapping("/clinics/{slug}/audit.csv")
    public ResponseEntity<byte[]> export(
            @PathVariable String slug,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            Principal master) {
        long id = clinics.idOf(slug);
        Filters filters = filters(action, role, from, to);
        // L'export reprend exactement les filtres affichés.
        String csv =
                db.inClinic(
                        id,
                        () -> {
                            List<Object> args = new ArrayList<>(arguments(id, filters));
                            args.add(EXPORT_MAX);
                            var lines = new StringBuilder("Date;Auteur;Rôle;Action;Objet\r\n");
                            for (Object[] r :
                                    jdbc.query(
                                            "select j.uid, j.created_at, j.actor_id, j.email,"
                                                    + " j.role, j.action, j.target, j.platform "
                                                    + UNION
                                                    + " order by j.created_at desc, j.uid desc limit ?",
                                            (rs, i) -> raw(rs),
                                            args.toArray())) {
                                Entry e = entry(id, r);
                                lines.append(
                                                String.join(
                                                        ";",
                                                        cell(CSV_DATE.format(e.at())),
                                                        cell(e.actor()),
                                                        cell(e.role()),
                                                        cell(e.action()),
                                                        cell(e.target())))
                                        .append("\r\n");
                            }
                            journal.record(
                                    master.getName(),
                                    PlatformJournal.AUDIT_EXPORTED,
                                    id,
                                    slug,
                                    null);
                            return lines.toString();
                        });
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"journal-" + slug + ".csv\"")
                // BOM : Excel reconnaît l'UTF-8 et les accents.
                .body(("﻿" + csv).getBytes(StandardCharsets.UTF_8));
    }

    /** Journal master : toutes les actions de l'équipe plateforme, de la plus récente à la plus ancienne. */
    @GetMapping("/audit")
    public MasterPage master(@RequestParam(required = false) Long cursor) {
        return db.global(
                () -> {
                    List<MasterEntry> rows =
                            jdbc.query(
                                    """
                            select p.id, p.created_at, u.email, p.action, p.clinic_slug, p.target
                            from platform_audit p join app_user u on u.id=p.actor_id
                            where (cast(? as bigint) is null or p.id < ?)
                            order by p.id desc limit ?
                            """,
                                    (rs, i) ->
                                            new MasterEntry(
                                                    rs.getLong(1),
                                                    rs.getTimestamp(2).toInstant(),
                                                    rs.getString(3),
                                                    rs.getString(4),
                                                    rs.getString(5),
                                                    rs.getString(6)),
                                    cursor,
                                    cursor,
                                    PAGE + 1);
                    boolean more = rows.size() > PAGE;
                    List<MasterEntry> page = more ? rows.subList(0, PAGE) : rows;
                    return new MasterPage(page, more ? page.getLast().id() : null);
                });
    }

    private static Object[] raw(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Object[] {
            rs.getString(1),
            rs.getObject(2, OffsetDateTime.class),
            rs.getLong(3),
            rs.getString(4),
            rs.getString(5),
            rs.getString(6),
            rs.getString(7),
            rs.getBoolean(8)
        };
    }

    // Un patient n'est jamais nommé : pseudonyme stable dans ce cabinet.
    private Entry entry(long clinic, Object[] r) {
        String role = (String) r[4];
        String actor = "PATIENT".equals(role) ? pseudonyms.of(clinic, (Long) r[2]) : (String) r[3];
        return new Entry(
                (String) r[0],
                ((OffsetDateTime) r[1]).toInstant(),
                actor,
                role,
                (String) r[5],
                (String) r[6],
                (Boolean) r[7]);
    }

    private static Filters filters(String action, String role, LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Période invalide.");
        return new Filters(
                action == null || action.isBlank() ? null : action,
                role == null || role.isBlank() ? null : role,
                from == null ? null : from.atStartOfDay(PlatformStatsController.PARIS).toOffsetDateTime(),
                to == null
                        ? null
                        : to.plusDays(1).atStartOfDay(PlatformStatsController.PARIS).toOffsetDateTime());
    }

    private static List<Object> arguments(long clinic, Filters f) {
        List<Object> args = new ArrayList<>();
        args.add(clinic);
        args.add(f.action());
        args.add(f.action());
        args.add(f.role());
        args.add(f.role());
        args.add(f.from());
        args.add(f.from());
        args.add(f.to());
        args.add(f.to());
        return args;
    }

    static String encode(OffsetDateTime at, String uid) {
        String raw = at.withOffsetSameInstant(ZoneOffset.UTC) + "|" + uid;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Cursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int bar = raw.indexOf('|');
            String uid = raw.substring(bar + 1);
            if (bar < 0 || !uid.matches("[ap]-\\d{1,19}")) throw new IllegalArgumentException();
            return new Cursor(OffsetDateTime.parse(raw.substring(0, bar)), uid);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Curseur de pagination invalide.");
        }
    }

    // Protection contre l'injection de formules dans un tableur.
    static String cell(String value) {
        if (value == null) return "";
        String text = value.matches("^[=+\\-@\\t\\r].*") ? "'" + value : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}

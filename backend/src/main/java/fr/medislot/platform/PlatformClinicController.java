package fr.medislot.platform;

import fr.medislot.repository.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** CRUD des cabinets pour la console master. Le master ne voit que des comptes, jamais un patient. */
@RestController
@RequestMapping("/api/platform/clinics")
public class PlatformClinicController {
    static final String SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";
    static final String NOT_FOUND = "Ce cabinet n'existe pas ou a été supprimé.";
    static final int LOGO_MAX = 200 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final PlatformDb db;
    private final PlatformJournal journal;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public PlatformClinicController(
            JdbcTemplate jdbc,
            PlatformDb db,
            PlatformJournal journal,
            UserRepository users,
            PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.db = db;
        this.journal = journal;
        this.users = users;
        this.encoder = encoder;
    }

    public record ClinicRow(
            long id,
            String slug,
            String name,
            String city,
            String address,
            String color,
            Instant createdAt,
            boolean hasLogo,
            long professionals,
            long appointments30d) {}

    public record PractitionerRow(
            long id, String name, String specialty, long appointments30d, Instant nextFreeSlot) {}

    public record ClinicDetail(ClinicRow clinic, String adminEmail, List<PractitionerRow> practitioners) {}

    public record CreateClinic(
            @NotBlank(message = "Saisissez le nom du cabinet.")
                    @Size(max = 120, message = "120 caractères au plus.")
                    String name,
            @NotBlank(message = "Saisissez l'identifiant du cabinet.")
                    @Size(min = 3, max = 40, message = "3 à 40 caractères.")
                    @Pattern(regexp = SLUG, message = "Minuscules, chiffres et tirets uniquement.")
                    String slug,
            @NotBlank(message = "Saisissez la ville.")
                    @Size(max = 80, message = "80 caractères au plus.")
                    String city,
            @Size(max = 200, message = "200 caractères au plus.") String address,
            @NotBlank(message = "Choisissez une couleur.")
                    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Couleur au format #RRGGBB.")
                    String color,
            @NotBlank(message = "Saisissez l'e-mail du premier administrateur.")
                    @Email(message = "Saisissez une adresse e-mail valide.")
                    @Size(max = 254, message = "254 caractères au plus.")
                    String adminEmail) {}

    // L'identifiant n'est pas modifiable : il n'apparaît pas dans la modification.
    public record UpdateClinic(
            @NotBlank(message = "Saisissez le nom du cabinet.")
                    @Size(max = 120, message = "120 caractères au plus.")
                    String name,
            @NotBlank(message = "Saisissez la ville.")
                    @Size(max = 80, message = "80 caractères au plus.")
                    String city,
            @Size(max = 200, message = "200 caractères au plus.") String address,
            @NotBlank(message = "Choisissez une couleur.")
                    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Couleur au format #RRGGBB.")
                    String color) {}

    public record Created(ClinicRow clinic, String adminEmail, String temporaryPassword) {}

    public record Deletion(
            @NotBlank(message = "Tapez l'identifiant du cabinet.") String confirmSlug,
            @NotBlank(message = "Saisissez votre mot de passe master.") @Size(max = 72)
                    String password) {}

    // Comptes agrégés : le cabinet courant (inClinic) ou la politique plateforme (acrossClinics)
    // rend les lignes visibles ; aucune donnée personnelle n'est lue.
    private static final String ROW =
            """
            select c.id, c.slug, c.name, c.city, c.address, c.color, c.created_at, c.logo is not null,
              (select count(*) from professional p where p.clinic_id=c.id),
              (select count(*) from appointment a where a.clinic_id=c.id
                 and a.created_at >= now() - interval '30 days')
            from clinic c
            """;

    private static final RowMapper<ClinicRow> ROW_MAPPER =
            (rs, i) ->
                    new ClinicRow(
                            rs.getLong(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6),
                            rs.getTimestamp(7).toInstant(),
                            rs.getBoolean(8),
                            rs.getLong(9),
                            rs.getLong(10));

    @GetMapping
    public List<ClinicRow> list(
            @RequestParam(required = false) String q, @RequestParam(required = false) String city) {
        String like = q == null || q.isBlank() ? null : "%" + escapeLike(q.strip()) + "%";
        String town = city == null || city.isBlank() ? null : city.strip();
        return db.acrossClinics(
                () ->
                        jdbc.query(
                                ROW
                                        + """
                                where (cast(? as text) is null or c.name ilike ? or c.slug ilike ?)
                                  and (cast(? as text) is null or c.city = ?)
                                order by c.name
                                """,
                                ROW_MAPPER,
                                like,
                                like,
                                like,
                                town,
                                town));
    }

    @GetMapping("/{slug}")
    public ClinicDetail detail(@PathVariable String slug) {
        long id = idOf(slug);
        return db.inClinic(
                id,
                () ->
                        new ClinicDetail(
                                row(id),
                                adminEmail(id),
                                jdbc.query(
                                        """
                                select p.id, p.name, p.specialty,
                                  (select count(*) from appointment a join slot s on s.id=a.slot_id
                                     where s.professional_id=p.id
                                       and a.created_at >= now() - interval '30 days'),
                                  (select min(s.starts_at) from slot s where s.professional_id=p.id
                                     and s.starts_at > now() and not exists (
                                       select 1 from appointment a
                                       where a.slot_id=s.id and a.status='CONFIRMED'))
                                from professional p order by p.name
                                """,
                                        (rs, i) ->
                                                new PractitionerRow(
                                                        rs.getLong(1),
                                                        rs.getString(2),
                                                        rs.getString(3),
                                                        rs.getLong(4),
                                                        rs.getTimestamp(5) == null
                                                                ? null
                                                                : rs.getTimestamp(5).toInstant()))));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Created create(@Valid @RequestBody CreateClinic input, Principal master) {
        String adminEmail = input.adminEmail().strip().toLowerCase(Locale.ROOT);
        String name = input.name().strip();
        String temporary = temporaryPassword();
        String hash = encoder.encode(temporary);
        return db.global(
                () -> {
                    if (Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    "select exists(select 1 from clinic where slug=?)",
                                    Boolean.class,
                                    input.slug())))
                        throw new FieldProblem(
                                HttpStatus.CONFLICT, "slug", "Cet identifiant est déjà utilisé.");
                    if (users.existsByEmail(adminEmail))
                        throw new FieldProblem(
                                HttpStatus.CONFLICT,
                                "adminEmail",
                                "Cette adresse a déjà un compte MediSlot.");
                    // Une seule transaction : cabinet + administrateur + ligne de journal.
                    Long id =
                            jdbc.queryForObject(
                                    """
                            insert into clinic(slug, name, city, address, color)
                            values (?, ?, ?, ?, ?) returning id
                            """,
                                    Long.class,
                                    input.slug(),
                                    name,
                                    input.city().strip(),
                                    blankToNull(input.address()),
                                    input.color().toLowerCase(Locale.ROOT));
                    String adminName = "Administration · " + name;
                    Long admin =
                            jdbc.queryForObject(
                                    """
                            insert into app_user(email, password, name, role)
                            values (?, ?, ?, 'ADMIN') returning id
                            """,
                                    Long.class,
                                    adminEmail,
                                    hash,
                                    adminName.substring(0, Math.min(adminName.length(), 120)));
                    jdbc.update(
                            "insert into membership(user_id, clinic_id) values (?, ?)", admin, id);
                    journal.record(
                            master.getName(),
                            PlatformJournal.CLINIC_CREATED,
                            id,
                            input.slug(),
                            name);
                    return new Created(row(id), adminEmail, temporary);
                });
    }

    @PutMapping("/{slug}")
    public ClinicRow update(
            @PathVariable String slug, @Valid @RequestBody UpdateClinic input, Principal master) {
        long id = idOf(slug);
        return db.inClinic(
                id,
                () -> {
                    var before = row(id);
                    String name = input.name().strip(), city = input.city().strip();
                    String address = blankToNull(input.address());
                    String color = input.color().toLowerCase(Locale.ROOT);
                    List<String> changed = new ArrayList<>();
                    if (!name.equals(before.name())) changed.add("Nom");
                    if (!city.equals(before.city())) changed.add("Ville");
                    if (!java.util.Objects.equals(address, before.address())) changed.add("Adresse");
                    if (!color.equalsIgnoreCase(before.color())) changed.add("Couleur");
                    jdbc.update(
                            "update clinic set name=?, city=?, address=?, color=? where id=?",
                            name,
                            city,
                            address,
                            color,
                            id);
                    if (!changed.isEmpty())
                        journal.record(
                                master.getName(),
                                PlatformJournal.CLINIC_UPDATED,
                                id,
                                slug,
                                String.join(", ", changed));
                    return row(id);
                });
    }

    @DeleteMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable String slug, @Valid @RequestBody Deletion input, Principal master) {
        if (!slug.equals(input.confirmSlug()))
            throw new FieldProblem(
                    HttpStatus.BAD_REQUEST,
                    "confirmSlug",
                    "L'identifiant saisi ne correspond pas.");
        // Nouvelle authentification : le mot de passe est revérifié côté back.
        var me = users.findByEmail(master.getName()).orElseThrow();
        if (me.password == null || !encoder.matches(input.password(), me.password))
            throw new FieldProblem(HttpStatus.BAD_REQUEST, "password", "Mot de passe incorrect.");
        long id = idOf(slug);
        // Le cabinet visé devient le cabinet courant : la sécurité par ligne limite chaque DELETE
        // à ses propres données.
        db.inClinic(
                id,
                () -> {
                    String name =
                            jdbc.queryForObject(
                                    "select name from clinic where id=?", String.class, id);
                    for (String table :
                            List.of(
                                    "notification",
                                    "calendar_subscription",
                                    "audit_event",
                                    "appointment",
                                    "slot",
                                    "professional"))
                        jdbc.update(
                                "delete from " + table + " where clinic_id = current_clinic()");
                    jdbc.update("delete from membership where clinic_id=?", id);
                    jdbc.update("delete from clinic where id=?", id);
                    journal.record(
                            master.getName(), PlatformJournal.CLINIC_DELETED, id, slug, name);
                    return null;
                });
    }

    @PutMapping(
            value = "/{slug}/logo",
            consumes = {"image/png", "image/svg+xml"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadLogo(
            @PathVariable String slug,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
            @RequestBody byte[] logo,
            Principal master) {
        String type = MediaType.parseMediaType(contentType).getSubtype().equals("png")
                ? "image/png"
                : "image/svg+xml";
        if (logo.length == 0 || logo.length > LOGO_MAX)
            throw new FieldProblem(HttpStatus.BAD_REQUEST, "logo", "Logo de 200 Ko maximum.");
        if (type.equals("image/png") ? !isPng(logo) : !isSafeSvg(logo))
            throw new FieldProblem(
                    HttpStatus.BAD_REQUEST, "logo", "Le fichier n'est pas un PNG ou un SVG valide.");
        long id = idOf(slug);
        db.global(
                () -> {
                    jdbc.update(
                            "update clinic set logo=?, logo_type=? where id=?", logo, type, id);
                    journal.record(master.getName(), PlatformJournal.LOGO_UPDATED, id, slug, null);
                    return null;
                });
    }

    @GetMapping("/{slug}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable String slug) {
        long id = idOf(slug);
        var logos =
                jdbc.query(
                        "select logo, logo_type from clinic where id=? and logo is not null",
                        (rs, i) -> new Object[] {rs.getBytes(1), rs.getString(2)},
                        id);
        if (logos.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun logo.");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType((String) logos.getFirst()[1]))
                .cacheControl(CacheControl.noCache())
                // Un SVG ouvert directement ne doit exécuter aucun script.
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .header("X-Content-Type-Options", "nosniff")
                .body((byte[]) logos.getFirst()[0]);
    }

    long idOf(String slug) {
        if (slug == null || slug.length() > 63 || !slug.matches(SLUG))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        return jdbc
                .query("select id from clinic where slug=?", (rs, i) -> rs.getLong(1), slug)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private ClinicRow row(long id) {
        return jdbc.queryForObject(ROW + " where c.id=?", ROW_MAPPER, id);
    }

    private String adminEmail(long id) {
        return jdbc
                .query(
                        """
                        select u.email from app_user u join membership m on m.user_id=u.id
                        where m.clinic_id=? and u.role='ADMIN' order by u.id limit 1
                        """,
                        (rs, i) -> rs.getString(1),
                        id)
                .stream()
                .findFirst()
                .orElse(null);
    }

    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static String temporaryPassword() {
        byte[] bytes = new byte[15];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static boolean isPng(byte[] data) {
        byte[] magic = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
        if (data.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) if (data[i] != magic[i]) return false;
        return true;
    }

    // Contrôle simple : un logo n'a besoin ni de script, ni de gestionnaire d'événement, ni de
    // contenu externe.
    static boolean isSafeSvg(byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return text.contains("<svg")
                && !text.contains("<script")
                && !text.contains("javascript:")
                && !text.contains("<foreignobject")
                && !text.matches("(?s).*\\son[a-z]+\\s*=.*");
    }
}

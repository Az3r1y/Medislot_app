package fr.medislot.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@org.springframework.transaction.annotation.Transactional
public class AbService {
    public static final String EXP = "booking-confirm-2026";

    public record Assignment(UUID subjectId, String experiment, String variant) {}

    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public AbService(JdbcTemplate jdbc, @Value("${medislot.ab-enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * Renvoie l'affectation existante ou la crée ; une affectation existante n'est jamais réécrite.
     */
    public Assignment assign(UUID subject) {
        jdbc.update(
                """
                INSERT INTO ab_assignment(subject_id, experiment, variant, assigned_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (subject_id) DO NOTHING
                """,
                subject,
                EXP,
                variantFor(EXP, subject.toString()));
        return jdbc.queryForObject(
                "SELECT subject_id,experiment,variant FROM ab_assignment WHERE subject_id=?",
                (rs, i) ->
                        new Assignment(
                                rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                subject);
    }

    /** Répartition stable : SHA-256, premier octet non signé, deux groupes de 128 valeurs. */
    static String variantFor(String experiment, String subject) {
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest((experiment + ":" + subject).getBytes(StandardCharsets.UTF_8));
            return Byte.toUnsignedInt(hash[0]) < 128 ? "A" : "B";
        } catch (NoSuchAlgorithmException e) {
            return "A";
        }
    }

    /**
     * Première exposition atomique : filtre et modification dans une seule requête, exposed_at
     * jamais remplacé.
     */
    public void expose(UUID subject) {
        jdbc.update(
                "UPDATE ab_assignment SET exposed=TRUE,exposed_at=now() WHERE subject_id=? AND exposed=FALSE",
                subject);
    }

    /**
     * Affectation à laquelle attribuer une réservation (exposition de moins de 24 h), sinon vide.
     */
    public Optional<Assignment> attribution(UUID subject) {
        if (!enabled || subject == null) return Optional.empty();
        return jdbc
                .query(
                        """
                        SELECT subject_id, experiment, variant FROM ab_assignment
                        WHERE subject_id = ? AND experiment = ?
                          AND exposed AND exposed_at > now() - INTERVAL '24 hours'
                        """,
                        (rs, i) ->
                                new Assignment(
                                        rs.getObject(1, UUID.class),
                                        rs.getString(2),
                                        rs.getString(3)),
                        subject,
                        EXP)
                .stream()
                .findFirst();
    }

    public Map<String, Object> metrics() {
        Map<String, Object> variants = new LinkedHashMap<>();
        for (String v : List.of("A", "B")) {
            Map<String, Object> m =
                    jdbc.queryForMap(
                            """
                            SELECT
                              (SELECT count(*) FROM ab_assignment
                                WHERE experiment = ? AND variant = ?) AS assigned,
                              (SELECT count(*) FROM ab_assignment
                                WHERE experiment = ? AND variant = ? AND exposed) AS exposed,
                              (SELECT count(DISTINCT ab_subject_id) FROM appointment
                                WHERE ab_experiment = ? AND ab_variant = ?) AS converted,
                              (SELECT count(*) FROM appointment
                                WHERE ab_experiment = ? AND ab_variant = ?) AS bookings
                            """,
                            EXP,
                            v,
                            EXP,
                            v,
                            EXP,
                            v,
                            EXP,
                            v);
            long exposed = ((Number) m.get("exposed")).longValue(),
                    converted = ((Number) m.get("converted")).longValue();
            m.put("rate", exposed > 0 ? (double) converted / exposed : null);
            variants.put(v, m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("experiment", EXP);
        out.put("enabled", enabled);
        out.put("variants", variants);
        return out;
    }
}

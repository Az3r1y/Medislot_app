package fr.medislot.platform;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Journal master : chaque action du super-administrateur y laisse une ligne, en ajout seul. */
@Component
public class PlatformJournal {
    public static final String LOGIN = "LOGIN",
            CLINIC_CREATED = "CLINIC_CREATED",
            CLINIC_UPDATED = "CLINIC_UPDATED",
            CLINIC_DELETED = "CLINIC_DELETED",
            LOGO_UPDATED = "LOGO_UPDATED",
            AUDIT_VIEWED = "AUDIT_VIEWED",
            AUDIT_EXPORTED = "AUDIT_EXPORTED",
            APPOINTMENTS_VIEWED = "APPOINTMENTS_VIEWED";

    private final JdbcTemplate jdbc;

    public PlatformJournal(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** À appeler dans une transaction en écriture. */
    public void record(
            String actorEmail, String action, Long clinicId, String clinicSlug, String target) {
        jdbc.update(
                """
                insert into platform_audit(actor_id, action, clinic_id, clinic_slug, target, created_at)
                select id, ?, ?, ?, ?, clock_timestamp() from app_user where email=?
                """,
                action,
                clinicId,
                clinicSlug,
                target == null ? null : target.substring(0, Math.min(target.length(), 160)),
                actorEmail);
    }
}

package fr.medislot.platform;

import fr.medislot.tenant.TenantContext;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Transactions de la console master. Aucune ne contourne la sécurité par ligne : soit la politique
 * « plateforme » est activée pour une lecture agrégée, soit le cabinet visé devient le cabinet
 * courant, comme pour une requête de ce cabinet.
 */
@Component
public class PlatformDb {
    private final TransactionTemplate write, read;
    private final JdbcTemplate jdbc;

    public PlatformDb(PlatformTransactionManager manager, JdbcTemplate jdbc) {
        this.write = new TransactionTemplate(manager);
        this.read = new TransactionTemplate(manager);
        this.read.setReadOnly(true);
        this.jdbc = jdbc;
    }

    /** Lecture de tous les cabinets, en lecture seule (politique RLS platform_read). */
    public <T> T acrossClinics(Supplier<T> work) {
        return withClinic(
                null,
                () ->
                        read.execute(
                                status -> {
                                    jdbc.queryForObject(
                                            "select set_config('app.platform', 'on', true)",
                                            String.class);
                                    return work.get();
                                }));
    }

    /** Travail limité à un seul cabinet : app.clinic_id vaut ce cabinet pour la transaction. */
    public <T> T inClinic(long clinic, Supplier<T> work) {
        return withClinic(clinic, () -> write.execute(status -> work.get()));
    }

    /** Tables sans cabinet (clinic, app_user, membership, platform_audit). */
    public <T> T global(Supplier<T> work) {
        return withClinic(null, () -> write.execute(status -> work.get()));
    }

    private static <T> T withClinic(Long clinic, Supplier<T> work) {
        Long previous = TenantContext.current();
        if (clinic == null) TenantContext.clear();
        else TenantContext.set(clinic);
        try {
            return work.get();
        } finally {
            if (previous == null) TenantContext.clear();
            else TenantContext.set(previous);
        }
    }
}

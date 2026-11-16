package fr.medislot.tenant;

public final class TenantContext {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static Long current() {
        return CURRENT.get();
    }

    public static long required() {
        Long id = current();
        if (id == null) throw new IllegalStateException("Aucun cabinet dans le contexte.");
        return id;
    }

    public static void set(Long id) {
        CURRENT.set(id);
    }

    public static void clear() {
        CURRENT.remove();
    }
}

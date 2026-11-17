-- Console master : comptes PLATFORM_ADMIN qui gèrent tous les cabinets.
-- V1 à V10 restent inchangées.

-- Informations complémentaires des cabinets (formulaire de la console).
ALTER TABLE clinic ADD COLUMN address VARCHAR(200);
ALTER TABLE clinic ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE clinic ADD COLUMN logo BYTEA;
ALTER TABLE clinic ADD COLUMN logo_type VARCHAR(20)
    CHECK (logo_type IN ('image/png', 'image/svg+xml'));
ALTER TABLE clinic ADD CONSTRAINT clinic_logo_complete CHECK ((logo IS NULL) = (logo_type IS NULL));
ALTER TABLE clinic ADD CONSTRAINT clinic_logo_size CHECK (logo IS NULL OR octet_length(logo) <= 204800);
GRANT INSERT, UPDATE, DELETE ON clinic TO medislot_app;
GRANT USAGE, SELECT ON SEQUENCE clinic_id_seq TO medislot_app;

-- Journal master : lignes en ajout seul. Le rôle applicatif n'a ni UPDATE ni DELETE.
-- Pas de clé étrangère vers clinic : la ligne survit à la suppression du cabinet.
CREATE TABLE platform_audit (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT NOT NULL REFERENCES app_user(id),
    action VARCHAR(40) NOT NULL,
    clinic_id BIGINT,
    clinic_slug VARCHAR(63),
    target VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX platform_audit_created_idx ON platform_audit(created_at DESC, id DESC);
CREATE INDEX platform_audit_clinic_idx ON platform_audit(clinic_id, created_at DESC);
GRANT SELECT, INSERT ON platform_audit TO medislot_app;
GRANT USAGE, SELECT ON SEQUENCE platform_audit_id_seq TO medislot_app;

-- Lecture plateforme : activée explicitement dans la transaction par
-- set_config('app.platform', 'on', true), jamais par un superutilisateur.
-- Lecture seule : les écritures restent limitées au cabinet courant.
CREATE FUNCTION platform_scope() RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
    SELECT coalesce(current_setting('app.platform', true), '') = 'on'
$$;
CREATE POLICY platform_read ON professional FOR SELECT USING (platform_scope());
CREATE POLICY platform_read ON slot FOR SELECT USING (platform_scope());
CREATE POLICY platform_read ON appointment FOR SELECT USING (platform_scope());

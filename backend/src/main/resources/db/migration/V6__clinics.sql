-- V1 à V5 restent inchangées : ce dépôt contient déjà les sessions et la géolocalisation.
CREATE TABLE clinic (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(63) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name VARCHAR(120) NOT NULL,
    city VARCHAR(80) NOT NULL,
    color VARCHAR(7) NOT NULL CHECK (color ~ '^#[0-9a-fA-F]{6}$')
);
INSERT INTO clinic (slug, name, city, color) VALUES
    ('cabinet-a', 'Cabinet des Tilleuls', 'Paris', '#176b5b'),
    ('cabinet-b', 'Maison de santé du Canal', 'Lyon', '#3955a5');

CREATE TABLE membership (
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    clinic_id BIGINT NOT NULL REFERENCES clinic(id),
    PRIMARY KEY (user_id, clinic_id)
);
-- Les utilisateurs existants conservent leur accès à A.
INSERT INTO membership SELECT id, (SELECT id FROM clinic WHERE slug='cabinet-a') FROM app_user;

CREATE FUNCTION current_clinic() RETURNS BIGINT LANGUAGE sql STABLE AS $$
    SELECT NULLIF(current_setting('app.clinic_id', true), '')::BIGINT
$$;

ALTER TABLE professional ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);
UPDATE professional SET clinic_id=(SELECT id FROM clinic WHERE slug =
    CASE WHEN city IN ('Lyon', 'Villeurbanne') THEN 'cabinet-b' ELSE 'cabinet-a' END);
ALTER TABLE professional ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE professional ALTER COLUMN clinic_id SET DEFAULT current_clinic();
ALTER TABLE professional ADD UNIQUE (clinic_id, id);
ALTER TABLE professional ADD UNIQUE (clinic_id, name, address);

ALTER TABLE slot ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);
UPDATE slot s SET clinic_id=p.clinic_id FROM professional p WHERE p.id=s.professional_id;
ALTER TABLE slot ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE slot ALTER COLUMN clinic_id SET DEFAULT current_clinic();
ALTER TABLE slot ADD UNIQUE (clinic_id, id);
ALTER TABLE slot ADD CONSTRAINT slot_clinic_professional_fk FOREIGN KEY (clinic_id, professional_id)
    REFERENCES professional(clinic_id, id);

ALTER TABLE appointment ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);
UPDATE appointment a SET clinic_id=s.clinic_id FROM slot s WHERE s.id=a.slot_id;
ALTER TABLE appointment ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE appointment ALTER COLUMN clinic_id SET DEFAULT current_clinic();
ALTER TABLE appointment ADD UNIQUE (clinic_id, id);
ALTER TABLE appointment ADD CONSTRAINT appointment_clinic_slot_fk FOREIGN KEY (clinic_id, slot_id)
    REFERENCES slot(clinic_id, id);
INSERT INTO membership SELECT DISTINCT patient_id, clinic_id FROM appointment ON CONFLICT DO NOTHING;
ALTER TABLE appointment ADD CONSTRAINT appointment_clinic_patient_fk FOREIGN KEY (patient_id, clinic_id)
    REFERENCES membership(user_id, clinic_id);

ALTER TABLE audit_event ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);
UPDATE audit_event e SET clinic_id=a.clinic_id FROM appointment a WHERE a.id=e.appointment_id;
ALTER TABLE audit_event ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE audit_event ALTER COLUMN clinic_id SET DEFAULT current_clinic();
ALTER TABLE audit_event ADD CONSTRAINT audit_clinic_appointment_fk FOREIGN KEY (clinic_id, appointment_id)
    REFERENCES appointment(clinic_id, id);
INSERT INTO membership SELECT DISTINCT actor_id, clinic_id FROM audit_event ON CONFLICT DO NOTHING;
ALTER TABLE audit_event ADD CONSTRAINT audit_clinic_actor_fk FOREIGN KEY (actor_id, clinic_id)
    REFERENCES membership(user_id, clinic_id);

-- Les anciennes notifications n'identifiaient aucun cabinet : conservation dans A.
ALTER TABLE notification ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);
UPDATE notification SET clinic_id=(SELECT id FROM clinic WHERE slug='cabinet-a');
ALTER TABLE notification ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE notification ALTER COLUMN clinic_id SET DEFAULT current_clinic();
ALTER TABLE notification ADD CONSTRAINT notification_clinic_patient_fk FOREIGN KEY (patient_id, clinic_id)
    REFERENCES membership(user_id, clinic_id);

CREATE INDEX professional_clinic_idx ON professional(clinic_id);
CREATE INDEX slot_clinic_idx ON slot(clinic_id);
CREATE INDEX appointment_clinic_patient_idx ON appointment(clinic_id, patient_id, created_at DESC);
CREATE INDEX audit_clinic_idx ON audit_event(clinic_id, appointment_id);
CREATE INDEX notification_clinic_idx ON notification(clinic_id, patient_id);

-- Identifiants de démonstration uniquement. En exploitation, provisionner ce rôle séparément.
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='medislot_app') THEN
        CREATE ROLE medislot_app LOGIN PASSWORD 'medislot-app-local-only'
            NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
END $$;
ALTER ROLE medislot_app NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOINHERIT;
GRANT USAGE ON SCHEMA public TO medislot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON app_user, membership, ab_assignment,
    spring_session, spring_session_attributes TO medislot_app;
GRANT SELECT ON clinic TO medislot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON professional, slot, appointment, audit_event, notification TO medislot_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO medislot_app;

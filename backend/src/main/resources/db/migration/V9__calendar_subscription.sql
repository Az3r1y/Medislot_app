-- Un lien personnel ne donne accès qu'au calendrier d'un seul cabinet.
CREATE TABLE calendar_subscription (
    user_id BIGINT NOT NULL,
    clinic_id BIGINT NOT NULL DEFAULT current_clinic(),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    PRIMARY KEY (user_id, clinic_id),
    FOREIGN KEY (user_id, clinic_id) REFERENCES membership(user_id, clinic_id)
);
GRANT SELECT, INSERT, UPDATE, DELETE ON calendar_subscription TO medislot_app;
ALTER TABLE calendar_subscription ENABLE ROW LEVEL SECURITY;
ALTER TABLE calendar_subscription FORCE ROW LEVEL SECURITY;
CREATE POLICY clinic_isolation ON calendar_subscription
    USING (clinic_id = current_clinic()) WITH CHECK (clinic_id = current_clinic());

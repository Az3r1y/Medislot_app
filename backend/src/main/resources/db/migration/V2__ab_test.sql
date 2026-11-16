CREATE TABLE ab_assignment (subject_id UUID PRIMARY KEY, experiment VARCHAR(80) NOT NULL, variant VARCHAR(1) NOT NULL CHECK(variant IN ('A','B')), assigned_at TIMESTAMPTZ NOT NULL, exposed BOOLEAN NOT NULL DEFAULT FALSE, exposed_at TIMESTAMPTZ, CHECK(exposed = (exposed_at IS NOT NULL)));
CREATE INDEX ab_assignment_metrics_idx ON ab_assignment(experiment, variant, exposed);
ALTER TABLE appointment ADD COLUMN ab_experiment VARCHAR(80), ADD COLUMN ab_variant VARCHAR(1), ADD COLUMN ab_subject_id UUID REFERENCES ab_assignment(subject_id);
CREATE INDEX appointment_ab_idx ON appointment(ab_experiment, ab_variant) WHERE ab_experiment IS NOT NULL;

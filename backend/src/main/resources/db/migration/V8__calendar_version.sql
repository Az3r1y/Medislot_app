ALTER TABLE appointment ADD COLUMN calendar_sequence INTEGER NOT NULL DEFAULT 0;
ALTER TABLE appointment ADD COLUMN calendar_updated_at TIMESTAMPTZ;
UPDATE appointment SET calendar_updated_at = created_at;
UPDATE appointment a SET
    calendar_sequence = history.changes,
    calendar_updated_at = GREATEST(a.created_at, history.updated_at)
FROM (
    SELECT appointment_id, COUNT(*)::INTEGER AS changes, MAX(created_at) AS updated_at
    FROM audit_event
    WHERE action IN ('RESCHEDULED', 'CANCELLED')
    GROUP BY appointment_id
) history
WHERE history.appointment_id = a.id;
ALTER TABLE appointment ALTER COLUMN calendar_updated_at SET NOT NULL;

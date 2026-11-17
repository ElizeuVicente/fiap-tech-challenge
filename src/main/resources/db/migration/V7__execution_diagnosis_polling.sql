ALTER TABLE execution_outbox ADD COLUMN diagnosis_received_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE execution_outbox ADD COLUMN diagnosis_checked_at TIMESTAMP WITH TIME ZONE;

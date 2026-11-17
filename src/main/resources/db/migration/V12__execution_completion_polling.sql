ALTER TABLE execution_outbox ADD COLUMN completion_received_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE execution_outbox ADD COLUMN completion_checked_at TIMESTAMP WITH TIME ZONE;

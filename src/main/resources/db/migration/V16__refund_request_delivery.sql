ALTER TABLE billing_refund_requests ADD COLUMN delivered_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE billing_refund_requests ADD COLUMN last_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE billing_refund_requests ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;

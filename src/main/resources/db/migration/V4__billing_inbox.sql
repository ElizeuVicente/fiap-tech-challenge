CREATE TABLE billing_inbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    order_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    saga_id UUID NOT NULL,
    correlation_id UUID NOT NULL,
    payload TEXT NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

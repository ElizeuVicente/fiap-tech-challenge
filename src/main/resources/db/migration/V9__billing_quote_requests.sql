CREATE TABLE billing_quote_requests (
    order_id UUID PRIMARY KEY,
    branch_id UUID NOT NULL,
    saga_id UUID NOT NULL,
    correlation_id UUID NOT NULL,
    amount NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP WITH TIME ZONE
);

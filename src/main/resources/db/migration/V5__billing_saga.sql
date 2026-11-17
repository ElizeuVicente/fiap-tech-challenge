CREATE TABLE billing_saga (
    saga_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    correlation_id UUID NOT NULL,
    quote_id UUID NOT NULL,
    status VARCHAR(40) NOT NULL,
    decided_event_id UUID,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_billing_saga_status CHECK (status IN ('AGUARDANDO_DECISAO','APROVADA','RECUSADA','COMPENSACAO_PENDENTE','FALHA'))
);
CREATE TABLE billing_command_outbox (
    command_id UUID PRIMARY KEY,
    saga_id UUID NOT NULL REFERENCES billing_saga(saga_id),
    command_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_billing_command_saga_type UNIQUE (saga_id, command_type)
);
CREATE INDEX ix_billing_command_outbox_pending ON billing_command_outbox (published_at, created_at);

ALTER TABLE billing_saga DROP CONSTRAINT ck_billing_saga_status;
ALTER TABLE billing_saga ADD CONSTRAINT ck_billing_saga_status CHECK (status IN
    ('AGUARDANDO_DECISAO','APROVADA','RECUSADA','COMPENSACAO_PENDENTE','FALHA','AGUARDANDO_ESTOQUE','PAGAMENTO_RECUSADO'));
ALTER TABLE billing_saga ADD COLUMN payment_attempt_id UUID;
ALTER TABLE billing_saga ADD COLUMN provider_payment_id VARCHAR(20);

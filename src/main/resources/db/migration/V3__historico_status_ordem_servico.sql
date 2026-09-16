CREATE TABLE historico_status_ordem_servico (
 id UUID PRIMARY KEY, status_anterior VARCHAR(40), novo_status VARCHAR(40) NOT NULL,
 data_hora TIMESTAMP NOT NULL, origem VARCHAR(100), correlation_id VARCHAR(128),
 ordem_servico_id UUID NOT NULL REFERENCES ordem_servico(id)
);
CREATE INDEX ix_historico_os_data ON historico_status_ordem_servico (ordem_servico_id, data_hora);

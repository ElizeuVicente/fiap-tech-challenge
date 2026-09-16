ALTER TABLE cliente ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ATIVO';
CREATE INDEX ix_cliente_cpf_status ON cliente (cpf_cnpj, status);

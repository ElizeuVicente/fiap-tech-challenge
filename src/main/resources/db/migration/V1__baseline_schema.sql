-- Baseline PostgreSQL. Alterações posteriores devem ser novas migrations versionadas.
CREATE TABLE cliente (
    id UUID PRIMARY KEY, nome VARCHAR(255), cpf_cnpj VARCHAR(255), email VARCHAR(255), telefone VARCHAR(255)
);
CREATE UNIQUE INDEX uk_cliente_cpf_cnpj ON cliente (cpf_cnpj);
CREATE TABLE veiculo (
    id UUID PRIMARY KEY, placa VARCHAR(255), marca VARCHAR(255), modelo VARCHAR(255), ano INTEGER, cliente_id UUID REFERENCES cliente(id)
);
CREATE TABLE servico (
    id UUID PRIMARY KEY, nome VARCHAR(255), preco_base NUMERIC(19,2), tempo_estimado_minutos INTEGER
);
CREATE TABLE peca (
    id UUID PRIMARY KEY, nome VARCHAR(255), preco NUMERIC(19,2), quantidade_estoque INTEGER
);
CREATE TABLE ordem_servico (
    id UUID PRIMARY KEY, cliente_id UUID REFERENCES cliente(id), veiculo_id UUID REFERENCES veiculo(id),
    status VARCHAR(255), diagnostico VARCHAR(255), data_criacao TIMESTAMP, data_inicio_execucao TIMESTAMP, data_finalizacao TIMESTAMP
);
CREATE TABLE item_servico (
    id UUID PRIMARY KEY, servico_id UUID REFERENCES servico(id), preco_aplicado NUMERIC(19,2), ordem_servico_id UUID REFERENCES ordem_servico(id)
);
CREATE TABLE item_peca (
    id UUID PRIMARY KEY, peca_id UUID REFERENCES peca(id), quantidade INTEGER, preco_aplicado NUMERIC(19,2), ordem_servico_id UUID REFERENCES ordem_servico(id)
);
CREATE TABLE notificacao_orcamento (
    id UUID PRIMARY KEY, ordem_servico_id UUID NOT NULL REFERENCES ordem_servico(id), decisao VARCHAR(255), origem VARCHAR(255),
    data_hora TIMESTAMP, identificador_externo VARCHAR(255) NOT NULL UNIQUE
);
CREATE TABLE usuario (
    id UUID PRIMARY KEY, username VARCHAR(255) UNIQUE, password VARCHAR(255), role VARCHAR(255)
);
CREATE INDEX ix_ordem_servico_status_criacao ON ordem_servico (status, data_criacao);

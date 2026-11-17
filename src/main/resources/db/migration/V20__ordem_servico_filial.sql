-- Historical orders remain unassigned until their branch can be reconciled.
ALTER TABLE ordem_servico ADD COLUMN filial_id UUID;
CREATE INDEX ix_ordem_servico_filial_id ON ordem_servico (filial_id);

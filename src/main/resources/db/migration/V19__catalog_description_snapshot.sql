ALTER TABLE item_peca ADD COLUMN descricao_snapshot VARCHAR(255);
ALTER TABLE item_servico ADD COLUMN descricao_snapshot VARCHAR(255);
UPDATE item_peca i SET descricao_snapshot = p.nome FROM peca p WHERE i.peca_id = p.id;
UPDATE item_servico i SET descricao_snapshot = s.nome FROM servico s WHERE i.servico_id = s.id;

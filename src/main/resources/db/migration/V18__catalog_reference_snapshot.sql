-- Preserve existing identifiers and frozen prices; do not remove legacy relationships.
ALTER TABLE item_peca ADD COLUMN referencia_externa_id UUID;
ALTER TABLE item_servico ADD COLUMN referencia_externa_id UUID;
UPDATE item_peca SET referencia_externa_id = peca_id WHERE peca_id IS NOT NULL;
UPDATE item_servico SET referencia_externa_id = servico_id WHERE servico_id IS NOT NULL;

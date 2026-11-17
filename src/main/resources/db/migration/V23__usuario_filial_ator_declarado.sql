ALTER TABLE usuario_filial_auditoria ADD COLUMN ator_declarado VARCHAR(255);
ALTER TABLE usuario_filial_auditoria ADD COLUMN motivo VARCHAR(500);

-- The declared operator is contextual metadata, not cryptographic proof of identity.
CREATE OR REPLACE FUNCTION auditar_usuario_filial() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO usuario_filial_auditoria(username, filial_id, operacao, ator_banco, ator_declarado, motivo)
        VALUES (NEW.username, NEW.filial_id, TG_OP, current_user,
                NULLIF(current_setting('oficina.ator_declarado', true), ''),
                NULLIF(current_setting('oficina.motivo_vinculo', true), ''));
        RETURN NEW;
    END IF;
    INSERT INTO usuario_filial_auditoria(username, filial_id, operacao, ator_banco, ator_declarado, motivo)
    VALUES (OLD.username, OLD.filial_id, TG_OP, current_user,
            NULLIF(current_setting('oficina.ator_declarado', true), ''),
            NULLIF(current_setting('oficina.motivo_vinculo', true), ''));
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

-- Explicit branch access. Populate only through an audited administrative process.
CREATE TABLE usuario_filial (
    username VARCHAR(255) NOT NULL REFERENCES usuario(username),
    filial_id UUID NOT NULL,
    PRIMARY KEY (username, filial_id)
);

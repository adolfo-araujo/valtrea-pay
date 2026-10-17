ALTER TABLE usuarios ADD COLUMN email_confirmado_em TIMESTAMPTZ;

-- Sessões do app. Guardamos só o hash do token: quem ler o banco não consegue usá-lo.
CREATE TABLE sessoes (
    id              UUID PRIMARY KEY,
    usuario_id      UUID        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) NOT NULL,
    expira_em       TIMESTAMPTZ NOT NULL,
    criado_em       TIMESTAMPTZ NOT NULL,
    revogado_em     TIMESTAMPTZ,
    CONSTRAINT uk_sessoes_token UNIQUE (token_hash)
);
CREATE INDEX ix_sessoes_usuario ON sessoes (usuario_id);

-- Códigos de 6 dígitos enviados por e-mail (confirmação de e-mail e recuperação de senha).
CREATE TABLE codigos_email (
    id              UUID PRIMARY KEY,
    usuario_id      UUID        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    finalidade      VARCHAR(20) NOT NULL,
    codigo_hash     VARCHAR(64) NOT NULL,
    tentativas      INTEGER     NOT NULL DEFAULT 0,
    expira_em       TIMESTAMPTZ NOT NULL,
    criado_em       TIMESTAMPTZ NOT NULL,
    usado_em        TIMESTAMPTZ
);
CREATE INDEX ix_codigos_email_usuario ON codigos_email (usuario_id, finalidade, criado_em DESC);

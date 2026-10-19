-- Conta do Valtrea Finance ligada a cada usuário. O código de conexão fica cifrado (AES-GCM).
CREATE TABLE integracoes_finance (
    usuario_id      UUID PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    token_cifrado   TEXT        NOT NULL,
    conectado_em    TIMESTAMPTZ NOT NULL
);

-- Fila de envios para o Finance. Entra na mesma transação do pagamento, então nada se perde se o
-- Finance estiver fora do ar: um processo em segundo plano envia e tenta de novo depois.
CREATE TABLE envios_finance (
    id                  UUID PRIMARY KEY,
    usuario_id          UUID         NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    tipo                VARCHAR(20)  NOT NULL,
    conteudo            TEXT         NOT NULL,
    tentativas          INTEGER      NOT NULL DEFAULT 0,
    proxima_tentativa   TIMESTAMPTZ  NOT NULL,
    enviado_em          TIMESTAMPTZ,
    ultimo_erro         VARCHAR(200),
    criado_em           TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_envios_finance_pendentes ON envios_finance (proxima_tentativa) WHERE enviado_em IS NULL;

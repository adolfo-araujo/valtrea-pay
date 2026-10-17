CREATE TABLE usuarios (
    id                        UUID PRIMARY KEY,
    nome                      VARCHAR(120) NOT NULL,
    email                     VARCHAR(254) NOT NULL,
    cpf                       VARCHAR(11)  NOT NULL,
    telefone                  VARCHAR(20)  NOT NULL,
    senha_hash                VARCHAR(100) NOT NULL,
    papel                     VARCHAR(20)  NOT NULL,
    tentativas_login_falhas   INTEGER      NOT NULL DEFAULT 0,
    bloqueado_ate             TIMESTAMPTZ,
    criado_em                 TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT uk_usuarios_cpf UNIQUE (cpf)
);

CREATE TABLE contas (
    id            UUID PRIMARY KEY,
    usuario_id    UUID          NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    saldo         NUMERIC(19,2) NOT NULL DEFAULT 0,
    atualizado_em TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_contas_usuario UNIQUE (usuario_id),
    CONSTRAINT ck_contas_saldo_nao_negativo CHECK (saldo >= 0)
);

CREATE TABLE transacoes (
    id                  UUID PRIMARY KEY,
    tipo                VARCHAR(20)   NOT NULL,
    conta_origem_id     UUID          REFERENCES contas (id),
    conta_destino_id    UUID          NOT NULL REFERENCES contas (id),
    valor               NUMERIC(19,2) NOT NULL,
    descricao           VARCHAR(140),
    chave_idempotencia  VARCHAR(64),
    criado_em           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_transacoes_valor_positivo CHECK (valor > 0),
    CONSTRAINT ck_transacoes_origem_destino CHECK (conta_origem_id IS NULL OR conta_origem_id <> conta_destino_id)
);

-- Uma mesma chave de idempotência só gera uma transação por conta de origem.
CREATE UNIQUE INDEX uk_transacoes_idempotencia
    ON transacoes (conta_origem_id, chave_idempotencia)
    WHERE chave_idempotencia IS NOT NULL;

CREATE INDEX ix_transacoes_origem ON transacoes (conta_origem_id, criado_em DESC);
CREATE INDEX ix_transacoes_destino ON transacoes (conta_destino_id, criado_em DESC);

-- Conta excluída: os dados pessoais são apagados, mas a conta e as transações ficam (obrigação legal de
-- guardar registros financeiros, e o extrato de quem recebeu continua íntegro).
ALTER TABLE usuarios ADD COLUMN excluido_em TIMESTAMPTZ;
ALTER TABLE usuarios ALTER COLUMN cpf DROP NOT NULL;

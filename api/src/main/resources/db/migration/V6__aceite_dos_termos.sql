-- Prova do aceite: qual versão dos Termos e da Política a pessoa aceitou, e quando.
-- Contas anteriores ficam vazias e veem o aviso de "Termos novos" no app.
ALTER TABLE usuarios ADD COLUMN termos_versao VARCHAR(20);
ALTER TABLE usuarios ADD COLUMN termos_aceitos_em TIMESTAMPTZ;

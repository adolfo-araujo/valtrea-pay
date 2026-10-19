-- Nome em minúsculas e sem acentos, para "joao" encontrar "João" na busca de contatos.
-- A API preenche esta coluna em todo cadastro; aqui só convertemos quem já existe.
ALTER TABLE usuarios ADD COLUMN nome_busca VARCHAR(120);

UPDATE usuarios SET nome_busca = lower(translate(nome,
    'áàâãäéèêëíìîïóòôõöúùûüçñÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇÑ',
    'aaaaaeeeeiiiiooooouuuucnAAAAAEEEEIIIIOOOOOUUUUCN'));

ALTER TABLE usuarios ALTER COLUMN nome_busca SET NOT NULL;

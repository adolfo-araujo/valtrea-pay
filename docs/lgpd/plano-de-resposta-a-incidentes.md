# Plano de Resposta a Incidentes de Segurança: Valtrea Pay

Documento interno. Base: LGPD arts. 46 a 48; Resolução CD/ANPD nº 15/2024. O plano completo, com o passo a passo, está no repositório do Valtrea Finance (`docs/lgpd/plano-de-resposta-a-incidentes.md`); aqui ficam as ações específicas do Pay.

**Responsável:** João Adolfo Martins Araújo · contato@valtrea.com.br · **Atualizado em:** 04/11/2026

## Conter (na hora)

| O que vazou ou aconteceu | O que fazer |
| --- | --- |
| Segredo do JWT (`VALTREA_JWT_SECRET`) | Gerar outro no Render: todos os tokens de acesso deixam de valer (o app pede login de novo) |
| Banco (`DATABASE_*`) | Trocar a senha no Neon e atualizar o Render e o segredo `BACKUP_DATABASE_URL` do GitHub |
| Chave de cifra do Finance (`VALTREA_CHAVE_CIFRA`) | Gerar outra; as conexões com o Finance param de funcionar e cada pessoa conecta de novo. No Finance, gerar códigos novos invalida os antigos |
| Chave do Resend (`MAIL_PASSWORD`) | Revogar no Resend e criar outra |
| Chave de assinatura do app (`*.jks`) | Pedir troca da chave de upload no Play Console |
| Conta de usuário invadida | Pedir "Esqueci a senha": a redefinição desconecta todos os aparelhos |
| Abuso ou robôs | Os limites por IP já barram; se precisar, reduzir `VALTREA_LIMITE_*` no Render |

## Avaliar e comunicar

Havendo risco ou dano relevante aos titulares, comunicar a **ANPD e os titulares em até 3 dias úteis** (Resolução CD/ANPD nº 15/2024), pelo canal oficial em gov.br/anpd, e registrar o incidente, mesmo os não comunicados, por pelo menos 5 anos.

Lembrete: o saldo do Pay é fictício, mas nome, e-mail e CPF são dados pessoais reais.

## Restaurar dados

Veja `docs/BACKUP.md`.

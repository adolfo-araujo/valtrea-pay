# Auditoria Jurídica: Valtrea Pay

**Data:** 04/11/2026 · **Gerado com:** skill blindagem-juridica (versão `66c8cef`) · **Modo:** corrigir

> ⚖️ Este relatório é uma auditoria técnica automatizada e **não constitui parecer jurídico**. Ele reduz riscos, mas não garante ausência de questionamentos. Antes de movimentar dinheiro real, é indispensável a revisão de um(a) advogado(a) de regulação de pagamentos.

## Placar

| # | Item | Antes | Depois | Observação |
|---|------|-------|--------|-----------|
| 1 | Política de privacidade | ⚠️ | ✅ | Responsável identificado, canal de dados, base legal por dado, fornecedores e retenção que batem com o código |
| 2 | Termos de uso | ❌ | ✅ | Criados em `/termos`: app de testes, saldo fictício, não é banco nem instituição de pagamento; aceite registrado com versão e data |
| 3 | Aviso de cookies | ➖ | ➖ | O app e as páginas não usam cookies nem rastreadores |
| 4 | Política de reembolso | ➖ | ➖ | Nada é vendido; o saldo é fictício |
| 5 | Formulários (LGPD) | ⚠️ | ✅ | Cadastro com "aceita os Termos e está ciente da Política" e aviso de saldo fictício |
| 6 | Só dados necessários | ⚠️ | ✅ | Celular opcional; CPF mantido por decisão sua, com a finalidade explicada |
| 7 | Scripts de rastreamento | ✅ | ✅ | Nenhum SDK de análise ou anúncio |
| 8 | Acessibilidade geral | ⚠️ | ✅ | Declaração em `/acessibilidade`; páginas com idioma, "Pular para o conteúdo" e foco visível |
| 9 | Texto alternativo | ✅ | ✅ | Ícones informativos com descrição, decorativos ignorados |
| 10 | Contraste | ❌ | ✅ | Verde do tema claro e cartão de saldo passaram de 2,3–3,3:1 para 5:1 ou mais |
| 11 | Navegação por teclado | ⚠️ | ✅ | Páginas com link de pular e foco visível; o app usa componentes Material 3 |
| 12 | Avaliações e depoimentos | ✅ | ✅ | Não há |
| 13 | Promessas e publicidade | ⚠️ | ✅ | Ficha da loja dizia "envie e receba dinheiro"; agora diz que é versão de testes com saldo fictício |
| 14 | Direitos autorais | ✅ | ✅ | Ícone e imagens feitos para o projeto |
| 15 | Dados da empresa | ⚠️ | ⚠️ | Responsável pessoa física (nome e cidade), como no Finance |
| 16 | Conformidade LGPD | ⚠️ | ✅ | Baixar meus dados, retenção automática, registro de operações, plano de incidentes, backup criptografado |
| 17 | Outros riscos jurídicos | ⚠️ | ⚠️ | Regulação de pagamentos antes de dinheiro real (veja abaixo) |

**Riscos restantes mais importantes:**
1. **Dinheiro real.** Antes de qualquer Pix ou saldo real, é preciso autorização do Banco Central ou parceria com instituição autorizada, regras de prevenção à lavagem de dinheiro, guarda legal de registros e novos Termos. Enquanto isso, publique só em teste fechado.
2. **Mensagens entre usuários.** A mensagem do pagamento é conteúdo de terceiros; os Termos já proíbem abuso e dão canal de denúncia (contato@).
3. **Pessoa física.** Igual ao Finance: suficiente enquanto for gratuito e de testes.

## O que foi corrigido

- **Política** (`api/src/main/resources/static/privacidade.html`): saiu "a lei exige guardar transações por 5 anos" e "registros de acesso por 6 meses", que não batiam com o código nem com saldo fictício; entraram os prazos reais.
- **Termos** (`termos.html`) e **acessibilidade** (`acessibilidade.html`), novos; `excluir-conta.html` revisado.
- **API:** celular opcional, senha com letras e números, aceite dos Termos (migração `V6`), `GET /api/v1/eu/dados`, `POST /api/v1/eu/termos`, rotina de retenção (`retencao/RetencaoService.java`), limites por IP e cabeçalhos (`protecao/`).
- **App:** links legais no cadastro e no perfil, "Baixar meus dados", aviso de Termos novos, cores com contraste AA.
- **Play Store:** ficha da loja e formulário de segurança de dados atualizados.

## O que depende de você

- [ ] 🟠 Configurar o backup: segredos `BACKUP_DATABASE_URL` e `BACKUP_PASSPHRASE` (veja `docs/BACKUP.md`).
- [ ] 🟠 No GitHub do valtrea-pay, ativar **Dependabot alerts** e **security updates** (Settings > Code security).
- [ ] 🟡 Conferir os termos de tratamento de dados (DPA) dos fornecedores.
- [ ] 🟡 Revisão por advogado(a) antes de abrir o app ao público ou movimentar dinheiro real.

## Perfil e respostas (para a próxima execução)

- **Tipo:** app Android + API de carteira digital de testes (saldo fictício) de um app de banco em desenvolvimento; integra com o Valtrea Finance.
- **Responsável:** João Adolfo Martins Araújo, pessoa física, Rio de Janeiro/RJ, contato@valtrea.com.br; sem encarregado nomeado (pequeno porte).
- **Público:** maiores de 18 anos, Brasil.
- **Decisões de 04/11/2026:** CPF obrigatório; celular opcional; cadastros nunca confirmados apagados após 30 dias; transações anônimas mantidas enquanto a outra pessoa tiver conta.
- **Mapa de dados:** `docs/lgpd/registro-de-operacoes.md`.

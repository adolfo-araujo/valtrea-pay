# Registro das Operações de Tratamento de Dados Pessoais: Valtrea Pay

Documento interno (LGPD art. 37), no formato simplificado para agente de tratamento de pequeno porte (Resolução CD/ANPD nº 2/2022). Não publicar.

- **Controlador:** João Adolfo Martins Araújo (pessoa física), Rio de Janeiro/RJ
- **Encarregado / canal:** sem encarregado indicado (dispensa de pequeno porte); canal: contato@valtrea.com.br
- **Elaborado em:** 04/11/2026 · **Revisar:** a cada 12 meses ou a cada nova funcionalidade que trate dados
- **Serviço:** Valtrea Pay (app Android e API em pay.valtrea.com.br), carteira de testes com saldo fictício

## Operações

| # | Operação | Titulares | Dados pessoais | Sensíveis? | Finalidade | Base legal | Onde no sistema | Operadores | Transferência internacional | Retenção | Segurança |
|---|----------|-----------|----------------|-----------|-----------|-----------|-----------------|-----------|-----------------------------|----------|-----------|
| 1 | Cadastro e login | Usuários (18+) | Nome, e-mail, CPF, celular (opcional), senha (hash), aceite dos Termos | Não | Criar e manter a conta | Art. 7º, V (celular: art. 7º, I) | `POST /api/v1/auth/cadastro`, `/auth/login` | Render, Neon | Sim (Render, EUA) | Enquanto a conta existir; nunca confirmados sem transações: 30 dias | bcrypt, JWT curto, sessões só como SHA-256, limites por IP, bloqueio por tentativas |
| 2 | Pagamentos de teste | Usuários | Transações (valor, data, pagador, recebedor, mensagem), saldo | Não | Prestar o serviço | Art. 7º, V | `/transferencias`, `/depositos`, `/transacoes` | Render, Neon | Sim (Render, EUA) | Enquanto uma das pessoas da transação tiver conta | Contas travadas na mesma transação, idempotência |
| 3 | Busca de contatos | Usuários | Nome e e-mail mascarado de outras pessoas | Não | Achar para quem pagar | Art. 7º, V | `/contatos` | Render, Neon | Sim (Render, EUA) | Não guarda a busca | Sem CPF, celular ou saldo |
| 4 | E-mails do app | Usuários | E-mail, nome, códigos de 6 dígitos | Não | Confirmar e-mail, recuperar senha, avisos de segurança e de exclusão | Art. 7º, V | `email/EnviadorEmail.java` | Resend | Não (São Paulo) | Códigos: 15 min, apagados 1 dia após vencer | Códigos só como HMAC, 5 tentativas |
| 5 | Integração com o Finance | Usuários que conectam | Valor, data, nome da outra pessoa, saldo | Não | Lançar pagamentos no Finance a pedido | Art. 7º, V | `finance/*` | Valtrea Finance (Render, Neon) | Sim (EUA) | Fila: 30 dias após o envio | Código cifrado com AES-256-GCM; só escrita no Finance |
| 6 | Proteção contra abuso | Usuários e visitantes | IP, contagem de pedidos, tentativas de login | Não | Bloquear força bruta e robôs | Art. 7º, IX | `protecao/*`, `usuarios.tentativas_login_falhas` | Render | Sim (EUA) | IP só em memória, até 15 min | — |
| 7 | Prova do aceite | Usuários | Versão e data do aceite dos Termos | Não | Comprovar o aceite | Art. 7º, VI | `POST /api/v1/eu/termos`, cadastro | Render, Neon | Sim (EUA) | Enquanto a conta existir | — |
| 8 | Baixar meus dados | Usuários | Todos os dados da conta | Não | Acesso e portabilidade | Art. 18, II e V | `GET /api/v1/eu/dados` | Render | Sim (EUA) | Não guarda o arquivo | Exige login; sem senha nem códigos |
| 9 | Backup | Todos os usuários | Cópia completa do banco | Não | Recuperar dados em caso de falha | Art. 7º, IX | `.github/workflows/backup.yml` | GitHub | Sim (EUA) | 30 dias | AES-256, senha fora do GitHub |
| 10 | Contato | Quem escreve para contato@ | Nome, e-mail, mensagem | Não | Responder pedidos e denúncias | Art. 7º, V | E-mail | ImprovMX, Google (Gmail) | Sim (EUA) | Até 24 meses após o último contato | — |
| 12 | Registro de erros e lentidão | Usuários | Dados técnicos do erro (rota modelo, tela, versão do app e do Android), sem nome, e-mail, CPF, saldo, IP ou imagens | Não | Corrigir falhas e lentidão | Art. 7º, IX | `api/.../monitoramento/Monitoramento.java`, `android/.../ValtreaApp.kt` | Sentry | Sim (EUA) | Prazo do plano do Sentry (até 90 dias) | `sendDefaultPii` desligado, sem usuário, pedido, captura de tela, hierarquia da tela nem toques; só com SENTRY_DSN definido |
| 13 | Painel de administração | Usuários (em conjunto) | Só contagens e somas, nenhum dado individual | Não | Acompanhar o uso do serviço | Art. 7º, IX | `GET /api/v1/admin/metricas` | Render, Neon | Sim (EUA) | Não guarda | Só para administradores com e-mail confirmado (papel ou VALTREA_ADMIN_EMAILS) |
| 11 | Registros de acesso da hospedagem | Usuários | IP, data e hora | Não | Segurança e funcionamento | Art. 7º, IX | Render (automático) | Render | Sim (EUA) | Prazo do provedor | — |

## Direitos do titular

- Corrigir nome e celular, baixar os dados e excluir a conta: no app, em Perfil.
- Excluir sem o app: `https://pay.valtrea.com.br/excluir-conta` (pedido por e-mail, resposta em até 15 dias).
- Demais pedidos: contato@valtrea.com.br, resposta em até 15 dias.

## Operadores

| Fornecedor | Serviço | País | Contrato/DPA verificado? |
|-----------|---------|------|--------------------------|
| Render | Hospedagem da API | EUA | Pendente |
| Neon | Banco de dados | Brasil (São Paulo) | Pendente |
| GitHub | Backup criptografado | EUA | Pendente |
| Resend | E-mails | Brasil (São Paulo) | Pendente |
| ImprovMX / Google (Gmail) | E-mail de contato | EUA | Pendente |
| Sentry (Functional Software) | Registro de erros e lentidão | EUA | Pendente |
| Google Play | Distribuição do app (controlador independente) | EUA | — |

## Pendências

- Conferir os termos de tratamento de dados (DPA) dos fornecedores (Resolução CD/ANPD nº 19/2024).
- Antes de movimentar dinheiro real: autorização do Banco Central ou parceria com instituição autorizada, revisão jurídica e novo registro (dados financeiros reais, prevenção à lavagem de dinheiro e guarda legal de registros passam a valer).

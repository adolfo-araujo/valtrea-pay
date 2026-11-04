# Segurança

Revisão de 04/11/2026 com os mesmos 20 itens usados no Valtrea Finance. O CI confere boa parte deles a cada mudança (testes de integração da API, gitleaks e auditoria de dependências).

| # | Item | Como está |
| --- | --- | --- |
| 1 | Esconder chaves de API | O app só tem o endereço da API. Segredos (JWT, chave de cifra, banco, Resend) ficam em variáveis de ambiente no Render; o `render.yaml` gera o segredo do JWT e a chave de cifra sozinho |
| 2 | Segredos fora do Git | `.env`, chaves de assinatura (`*.jks`, `keystore.properties`) no `.gitignore`. O CI roda o **gitleaks** em todo o histórico e reprova se aparecer senha ou chave |
| 3 | Chave pública do banco | Não se aplica: o app nunca fala com o banco, só a API |
| 4 | RLS | Não se aplica do jeito do Supabase/Firebase. Toda consulta passa pela API, que usa o usuário do token (quem paga vem do token, nunca do corpo do pedido) |
| 5 | Criptografia | HTTPS obrigatório (o app de produção não aceita HTTP); banco Neon criptografado em repouso (AES-256); senhas com bcrypt; tokens de sessão só como SHA-256; códigos de e-mail só como HMAC; código de conexão com o Finance cifrado com AES-256-GCM; backup diário com AES-256 |
| 6 | Autenticação no servidor | JWT HS256 conferido pelo Spring Security em toda rota que não é pública; token de acesso de 15 min; sessão de 30 dias, cada token de sessão vale uma vez (reuso derruba todas as sessões) |
| 7 | Restringir acessos | Cada pessoa só vê o próprio saldo e extrato; a busca de contatos não mostra CPF, celular nem saldo e mascara o e-mail |
| 8 | Mass assignment | Pedidos são `record`s com só os campos permitidos e validação (Bean Validation); nada do corpo vira entidade direto |
| 9 | Cookies | Não se aplica: a API é sem estado (`STATELESS`) e o app guarda os tokens no armazenamento privado, fora do backup do Android |
| 10 | Hash nas senhas | bcrypt (`DelegatingPasswordEncoder`); senha nova com 8 a 72 caracteres, letras e números |
| 11 | Rate limit | Por IP: 300 pedidos a cada 5 min; 30 tentativas de login a cada 15 min; 20 cadastros e pedidos de e-mail a cada 15 min (429 com `Retry-After`). Por conta: bloqueio de 15 min após 5 senhas erradas, com aviso por e-mail; um código de e-mail por minuto e 5 tentativas por código |
| 12 | Proteção contra robôs | Limites por IP, confirmação de e-mail obrigatória para pagar e CPF válido e único por conta |
| 13 | Queries parametrizadas | JPA/Spring Data com parâmetros; a busca de contatos remove `%` e `_` do texto; a rotina de retenção usa SQL fixo com parâmetros |
| 14 | Validação dos dados | Bean Validation em todos os pedidos; valores em `BigDecimal` com no máximo 2 casas; corpo limitado a 100 KB (413) |
| 15 | Vazamento de conteúdo | Erros no formato ProblemDetail, sem detalhes internos (falha inesperada vira 500 com mensagem genérica e vai para o Sentry sem dados pessoais); o log de lentidão não grava a busca (nomes); os logs HTTP do app só existem na versão de desenvolvimento |
| 16 | Restringir uploads | Não se aplica: a API não recebe arquivos (o QR Code é lido no próprio celular) |
| 17 | Respostas enxutas | Respostas são `record`s com só o que o app usa; nada de hash, tokens guardados ou dados de outras pessoas além de nome e e-mail mascarado |
| 18 | Cabeçalhos de segurança | HSTS, `X-Content-Type-Options`, `X-Frame-Options`, `Cache-Control` (Spring Security) + CSP, `Referrer-Policy: no-referrer`, `Permissions-Policy` e `Cross-Origin-Opener-Policy` (`ProtecaoFilter`) |
| 19 | Forçar HTTPS | Render e Cloudflare redirecionam para HTTPS; o app de produção bloqueia HTTP (`network_security_config.xml`) |
| 20 | Varredura de dependências | OSV-Scanner no CI (reprova falha crítica na API) e Dependabot semanal para Maven, Gradle, Docker e GitHub Actions |

Outros cuidados de um app de pagamento:

- **Dinheiro:** saldo conferido antes do débito; débito e crédito na mesma transação com as duas contas travadas; saldo nunca negativo (restrição no banco); `Idempotency-Key` impede pagar duas vezes; 20 transferências simultâneas testadas a cada mudança.
- **App Android:** sem backup do Android (`allowBackup=false`), código ofuscado e reduzido na versão de produção (R8), apenas a permissão de internet (a câmera do QR Code é do Google Play Services).
- **Monitoramento (Sentry):** erros inesperados da API, travamentos do app e respostas acima de 1 segundo viram avisos no Sentry, com e-mail (veja `docs/TESTES.md`). Nada de usuário, IP, corpo, cabeçalhos ou captura de tela; as rotas vão como modelo (`/api/v1/contatos/{id}`). Sem `SENTRY_DSN`, nada sai do servidor.
- **Painel de administração:** só números somados (`GET /api/v1/admin/metricas`), só para contas com e-mail confirmado e papel `ADMINISTRADOR` ou e-mail listado em `VALTREA_ADMIN_EMAILS`. Quem não é admin recebe 403.

Ponto de atenção que fica: o cadastro avisa quando o e-mail ou o CPF já tem conta, o que permite descobrir se alguém usa o Valtrea Pay. É comum em apps, e o limite de 20 cadastros a cada 15 minutos por IP impede a varredura em massa.

# Testes, carga e alertas

## O que roda a cada mudança (CI)

Todo pull request e todo push na `main` rodam, no GitHub Actions:

| Etapa | O que verifica |
| --- | --- |
| API (testes com PostgreSQL real) | `mvn verify` com Testcontainers: cadastro, login, depósito, busca, transferências (inclusive 20 simultâneas), idempotência, extrato, confirmação de e-mail, sessões, recuperação de senha, bloqueio de login, integração com o Finance, exclusão de conta, limites por IP, 413, cabeçalhos, celular opcional, "Baixar meus dados", aceite dos Termos, rotina de retenção, painel de administração (403 para quem não é admin, sem dados pessoais) e Sentry (sem dados pessoais, um aviso de lentidão por rota a cada 15 min) |
| App Android | Testes unitários, testes das telas (Robolectric, sem emulador) com verificações de acessibilidade, e build da versão debug (o APK fica nos Artifacts por 14 dias) |
| Teste de carga | 100 pessoas usando ao mesmo tempo por 3 minutos (`loadtest/`), com a API limitada a 0,5 CPU e 512 MB (plano Starter do Render) e 120 ms de atraso até o banco (API nos EUA, banco em São Paulo). Reprova se 5% das respostas passarem de 1 segundo (login: 2 segundos) ou se mais de 1% der erro |
| Dependências da API | OSV-Scanner no `pom.xml` e em tudo o que ele puxa; reprova falha crítica |
| Segredos | gitleaks em todo o histórico do Git |

### Rodar o teste de carga no computador

Com Docker e [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) instalados, na raiz do repositório:

```bash
USERS=100 CPUS=0.5 MEMORY=512m DB_LATENCY_MS=120 bash loadtest/run.sh
```

## Testes das telas e acessibilidade (app)

Os testes em `android/app/src/test` abrem as telas de verdade (Jetpack Compose sobre Robolectric) e conferem:

| Tela | O que é verificado |
| --- | --- |
| Entrar | Campos com rótulo, senha escondida, cadastro com CPF e celular opcional, aviso de saldo fictício, links dos Termos e da Política, "esqueci a senha" e voltar, nos temas claro e escuro |
| Aviso de novos Termos | Links e botão "Li e concordo" |
| Administração | Números lidos linha a linha pelo leitor de tela ("Ativas: 12"), títulos de seção marcados como título, gráfico com descrição em texto, erro sem números |

Em todas as telas acima, `verificarAcessibilidade()` (em `ui/Acessibilidade.kt`) reprova se encontrar elemento tocável sem nome para o leitor de tela (WCAG 4.1.2), campo de texto sem rótulo (WCAG 1.3.1 e 3.3.2) ou alvo de toque menor que 24 x 24 dp (WCAG 2.5.8). O `ContrasteTest` confere o contraste mínimo de 4,5:1 (WCAG 1.4.3) entre texto e fundo em todas as combinações de cores dos temas claro e escuro. Esse teste encontrou o cinza do texto secundário com 4,4:1 sobre os cartões no tema claro; ele passou para #4B5563 (6,9:1).

O que esses testes não substituem: usar o app com o TalkBack ligado e com a fonte do Android no tamanho máximo, antes de cada versão na Play Store.

Rodar no computador: `cd android && ./gradlew testDebugUnitTest`.

## Monitoramento de erros e alerta de lentidão (Sentry)

A API e o app mandam para o Sentry, quando configurados:

- **API:** todo erro inesperado (que vira "Algo deu errado" no app) e toda resposta acima de 1 segundo ("Resposta lenta: GET /api/v1/transacoes levou 1530 ms"), agrupada por rota e no máximo um aviso a cada 15 minutos por rota. Também aparece no log do Render como `[lento]`.
- **App:** travamentos, erros e "app não responde" (ANR).

Nada de dado pessoal vai junto: sem usuário, IP, corpo ou cabeçalhos dos pedidos, captura de tela, hierarquia da tela nem toques registrados; as rotas vão como modelo (`/api/v1/contatos/{id}`).

### Como ligar

1. Crie uma conta grátis em [sentry.io](https://sentry.io) (ou use a do Valtrea Finance) e dois projetos: **valtrea-pay-api** (plataforma Java) e **valtrea-pay-android** (Android). Na criação, em "Data Storage", prefira a região dos EUA ou a mesma do Finance.
2. **API:** no Render, em **valtrea-pay-api** > **Environment**, adicione `SENTRY_DSN` com o DSN do projeto Java (Settings > Client Keys). Salve; o Render publica de novo.
3. **App:** ao gerar a versão para a Play Store, defina a variável `VALTREA_SENTRY_DSN` com o DSN do projeto Android (no Windows: `set VALTREA_SENTRY_DSN=https://...` antes do `gradlew bundleRelease`) ou coloque `valtreaSentryDsn=https://...` em `%USERPROFILE%\.gradle\gradle.properties`. Sem isso, o app não envia nada.
4. Nos dois projetos, em **Settings** > **Security & Privacy**, ligue **Prevent Storing of IP Addresses**.

### Receber o e-mail na hora

1. No Sentry, **Alerts** > **Create Alert** > **Issues**, projeto **valtrea-pay-api**.
2. Em "When": **A new issue is created** e **The issue changes state from resolved to unresolved**.
3. Em "Then": **Send a notification to** você (e-mail). Salve como "Erros do Pay".
4. Repita para a lentidão, com o filtro **The event's tags match** `tipo` igual a `resposta-lenta`, e salve como "Resposta lenta do Pay".
5. Repita o passo 1 a 3 no projeto **valtrea-pay-android** ("Erros do app").

Depois de corrigir, marque o aviso como **Resolved**: se voltar a acontecer, chega outro e-mail. Para mudar o limite de lentidão, defina `VALTREA_RESPOSTA_LENTA` no Render (ex.: `800ms`).

## Teste de capacidade (sob demanda)

Em **GitHub > Actions > Teste de capacidade > Run workflow** dá para escolher o cenário (100 ou 1000 pessoas e o plano do Render) ou rodar todos. Cada cenário sobe uma cópia isolada (PostgreSQL 18 e a API em Docker com a CPU e a memória do plano; "2x" são duas cópias atrás de um balanceador) e simula as pessoas por 3 minutos, depois de 1 a 3 minutos de chegada. Antes, a **sondagem da produção** faz uns 30 pedidos de leitura em pay.valtrea.com.br (sem criar nada) para medir o tempo de ida e volta entre o Render (EUA) e o Neon (São Paulo), que é usado como atraso no banco dos testes. Também confere os cabeçalhos de segurança e se as páginas públicas estão no ar.

O resultado de cada cenário aparece no resumo da execução e fica nos Artifacts por 30 dias.

RESULTADOS_CAPACIDADE

## Plano gratuito do Render

O plano Free tem 0,1 CPU e desliga depois de 15 minutos sem uso. Ele serve para a fase de testes com poucas pessoas, mas não para 100 ao mesmo tempo: o teste de carga do CI simula o plano Starter (0,5 CPU), o mesmo critério usado no Valtrea Finance.

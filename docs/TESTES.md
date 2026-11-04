# Testes, carga e alertas

## O que roda a cada mudança (CI)

Todo pull request e todo push na `main` rodam, no GitHub Actions:

| Etapa | O que verifica |
| --- | --- |
| API (testes com PostgreSQL real) | `mvn verify` com Testcontainers: cadastro, login, depósito, busca, transferências (inclusive 20 simultâneas), idempotência, extrato, confirmação de e-mail, sessões, recuperação de senha, bloqueio de login, integração com o Finance, exclusão de conta, limites por IP, 413, cabeçalhos, celular opcional, "Baixar meus dados", aceite dos Termos e rotina de retenção |
| App Android | Testes unitários e build da versão debug (o APK fica nos Artifacts por 14 dias) |
| Teste de carga | 100 pessoas usando ao mesmo tempo por 3 minutos (`loadtest/`), com a API limitada a 0,5 CPU e 512 MB (plano Starter do Render) e 120 ms de atraso até o banco (API nos EUA, banco em São Paulo). Reprova se 5% das respostas passarem de 1 segundo (login: 2 segundos) ou se mais de 1% der erro |
| Dependências da API | OSV-Scanner no `pom.xml` e em tudo o que ele puxa; reprova falha crítica |
| Segredos | gitleaks em todo o histórico do Git |

### Rodar o teste de carga no computador

Com Docker e [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) instalados, na raiz do repositório:

```bash
USERS=100 CPUS=0.5 MEMORY=512m DB_LATENCY_MS=120 bash loadtest/run.sh
```

## Alerta de lentidão

Toda resposta da API que passa de 1 segundo vira uma linha no log do Render: `[lento] GET /api/v1/transacoes levou 1530 ms`. Para ver: Render > **valtrea-pay-api** > **Logs** e busque por `[lento]`.

Para receber e-mail na hora, encaminhe os logs para um serviço com alertas (no Render: **Log Streams**) ou, no futuro, ligue o Sentry na API, como no Valtrea Finance.

Para mudar o limite, defina `VALTREA_RESPOSTA_LENTA` no Render (ex.: `800ms`).

## Plano gratuito do Render

O plano Free tem 0,1 CPU e desliga depois de 15 minutos sem uso. Ele serve para a fase de testes com poucas pessoas, mas não para 100 ao mesmo tempo: o teste de carga do CI simula o plano Starter (0,5 CPU), o mesmo critério usado no Valtrea Finance.

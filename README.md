# Valtrea Pay

Carteira digital da Valtrea: crie uma conta, coloque saldo e pague outras pessoas pelo nome ou e-mail.

Este projeto nasceu do "PicPay Clone" da mentoria DIO (API Spring Boot 2 + app Android de 2020) e foi refeito do zero com as tecnologias atuais e a marca Valtrea.

| Pasta | O que é | Tecnologias |
| --- | --- | --- |
| [`api/`](api) | API REST da carteira | Java 21, Spring Boot 4.1, Spring Security (JWT), JPA, PostgreSQL 18, Flyway, springdoc (Swagger), Testcontainers |
| [`android/`](android) | App Android | Kotlin 2.4, Jetpack Compose (Material 3), Navigation com rotas tipadas, Retrofit 3, kotlinx.serialization, DataStore, AGP 9.4, Gradle 9.8 |
| [`compose.yaml`](compose.yaml) | Banco + API para desenvolvimento | Docker Compose |

## Rodar

**Requisitos:** Docker Desktop, e o Android Studio para o app.

1. Suba o banco e a API:

   ```bash
   docker compose up --build
   ```

   - API: http://localhost:8080
   - Documentação interativa: http://localhost:8080/swagger-ui.html

2. Abra a pasta `android/` no Android Studio e rode no emulador. A versão debug já aponta para `http://10.0.2.2:8080`, que é o seu computador visto de dentro do emulador.

3. No app, crie duas contas (o CPF precisa ser válido), toque em **Depositar** para colocar saldo de teste e pague a outra conta.

> Para testar num celular de verdade, troque `API_URL` do `debug` em `android/app/build.gradle.kts` pelo IP do computador na rede (ex.: `http://192.168.0.10:8080/`) e adicione esse IP em `app/src/debug/res/xml/network_security_config.xml`.

## Testes

A API tem testes de integração que sobem um PostgreSQL real via Testcontainers e percorrem a API por HTTP: cadastro, login, depósito, busca de contatos, transferência, idempotência, extrato, bloqueio de login e 20 transferências simultâneas.

```bash
cd api
docker run --rm -v "$PWD:/build" -w /build -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9-eclipse-temurin-21 mvn -B verify
```

Com Java 21 e Maven instalados, basta `mvn verify` dentro de `api/`.

## Endpoints

Todos ficam em `/api/v1`. Os que não são de `auth` exigem `Authorization: Bearer <token>`.

| Método | Rota | O que faz |
| --- | --- | --- |
| POST | `/auth/cadastro` | Cria usuário e carteira com saldo zero; devolve o token |
| POST | `/auth/login` | Entra com e-mail e senha |
| GET | `/eu` | Perfil e saldo de quem está logado |
| GET | `/contatos?busca=` | Procura destinatários por e-mail completo ou parte do nome |
| POST | `/transferencias` | Paga outro usuário. Aceita o cabeçalho `Idempotency-Key` |
| GET | `/transacoes?pagina=&tamanho=` | Extrato paginado |
| POST | `/depositos` | Saldo de teste; só existe com `VALTREA_SANDBOX=true` |

## O que mudou em relação ao projeto original

**Segurança e dinheiro**
- Quem paga vem do token, não do corpo da requisição. No original, qualquer pessoa logada podia pagar em nome de outra.
- O saldo é conferido antes do débito, e débito e crédito acontecem na mesma transação de banco, com as duas contas travadas. No original, o saldo era atualizado em segundo plano, sem conferência, e podia ficar negativo.
- Valores em `BigDecimal`/`NUMERIC(19,2)` em vez de `Double`, para não perder centavos.
- `Idempotency-Key`: se a internet cair e o app reenviar o pagamento, ele não é cobrado duas vezes.
- A busca de contatos não devolve CPF, telefone nem saldo de outras pessoas, e mascara o e-mail. No original, `/usuarios/contatos` listava todos os usuários com CPF.
- Login bloqueado por 15 minutos após 5 senhas erradas; resposta igual para e-mail inexistente e senha errada.
- Segredo do JWT via variável de ambiente (no original, estava no repositório). Tokens de 30 minutos.
- Banco versionado com Flyway (`ddl-auto: validate`) em vez de `ddl-auto: update`.
- O app só usa HTTPS na versão de produção, e o token não vai para o backup do Android.

**Removido por enquanto:** o cadastro de cartão de crédito. O original salvava número e CVV no próprio banco, o que não pode ser feito fora de um ambiente certificado PCI. O caminho certo é usar um provedor de pagamentos (ex.: Stripe, Pagar.me, Mercado Pago), que guarda o cartão e devolve só um token.

## Próximos passos

- [ ] Entrada de dinheiro real (Pix via provedor de pagamentos) no lugar do depósito de teste
- [ ] Renovação automática do token (refresh token) e confirmação de e-mail
- [ ] Pagamento por QR Code
- [ ] Recuperação de senha
- [ ] CI no GitHub Actions rodando os testes da API e o build do app
- [ ] Integração com o Valtrea Finance (lançar os pagamentos como transações do orçamento)

## Autor

**João Adolfo** · [Valtrea](https://www.valtrea.com.br) · contato@valtrea.com.br

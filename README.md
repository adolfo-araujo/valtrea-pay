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
   docker compose up -d --build
   ```

   - API: http://localhost:8080
   - Documentação interativa: http://localhost:8080/swagger-ui.html
   - E-mails enviados pela API (códigos de confirmação e de senha): http://localhost:8025 (Mailpit; nada sai para a internet)

2. Abra a pasta `android/` no Android Studio e rode no emulador. A versão debug já aponta para `http://10.0.2.2:8080`, que é o seu computador visto de dentro do emulador.

3. No app, crie duas contas (o CPF precisa ser válido) e confirme o e-mail de cada uma com o código que aparece no Mailpit. Toque em **Depositar** para colocar saldo de teste e pague a outra conta.

> Para testar num celular de verdade, troque `API_URL` do `debug` em `android/app/build.gradle.kts` pelo IP do computador na rede (ex.: `http://192.168.0.10:8080/`) e adicione esse IP em `app/src/debug/res/xml/network_security_config.xml`.

## Testes

A API tem testes de integração que sobem um PostgreSQL real via Testcontainers e percorrem a API por HTTP: cadastro, login, depósito, busca de contatos, transferência, idempotência, extrato, 20 transferências simultâneas, confirmação de e-mail, renovação e reuso de sessão, saída, recuperação de senha, bloqueio de login com aviso por e-mail e a integração com o Finance (contra um Finance falso que simula queda e código revogado).

```bash
cd api
docker run --rm -v "$PWD:/build" -w /build -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9-eclipse-temurin-21 mvn -B verify
```

Com Java 21 e Maven instalados, basta `mvn verify` dentro de `api/`.

## Endpoints

Todos ficam em `/api/v1`. Exigem `Authorization: Bearer <accessToken>` os que não são de `auth`, além de `confirmar-email` e `reenviar-confirmacao`.

| Método | Rota | O que faz |
| --- | --- | --- |
| POST | `/auth/cadastro` | Cria usuário e carteira com saldo zero, envia o código de confirmação e devolve os tokens |
| POST | `/auth/login` | Entra com e-mail e senha |
| POST | `/auth/renovar` | Troca o `refreshToken` por um par novo (cada um vale uma vez) |
| POST | `/auth/sair` | Encerra a sessão do aparelho |
| POST | `/auth/confirmar-email` | Confirma o e-mail com o código de 6 números |
| POST | `/auth/reenviar-confirmacao` | Envia outro código (um por minuto) |
| POST | `/auth/esqueci-senha` | Envia código para redefinir a senha; resposta igual exista ou não a conta |
| POST | `/auth/redefinir-senha` | Nova senha com o código; desconecta todos os aparelhos |
| GET | `/eu` | Perfil e saldo de quem está logado |
| GET | `/contatos?busca=` | Procura destinatários por e-mail completo ou parte do nome |
| GET | `/contatos/{id}` | Mostra quem vai receber a partir do QR Code ou do link `valtrea://pagar/<id>` |
| POST | `/transferencias` | Paga outro usuário. Aceita o cabeçalho `Idempotency-Key` |
| GET | `/transacoes?pagina=&tamanho=` | Extrato paginado |
| POST | `/depositos` | Saldo de teste; só existe com `VALTREA_SANDBOX=true` |
| GET | `/integracoes/finance` | Se a conta está ligada ao Valtrea Finance |
| PUT | `/integracoes/finance` | Liga ao Finance com o código gerado no painel dele |
| DELETE | `/integracoes/finance` | Desliga do Finance |

## Integração com o Valtrea Finance

Com a conta ligada, cada pagamento **enviado** entra no Finance como gasto e cada pagamento **recebido**, como ganho, ambos na categoria "outros". O painel do Finance também mostra o saldo da carteira. Depósitos não viram ganho, porque é dinheiro da própria pessoa entrando na carteira; eles só atualizam o saldo.

1. No painel do Finance, no cartão **Carteira Valtrea Pay**, a pessoa toca em **Conectar** e copia o código (`vfp_...`).
2. No app Pay, em **Perfil → Valtrea Finance**, ela cola o código e toca em **Conectar**.

Como funciona:
- O código só serve para lançar pagamentos e atualizar o saldo. Ele não lê nada do Finance e não serve como login. O Finance guarda só o hash; o Pay guarda o código cifrado com AES-256-GCM.
- Os envios entram numa fila gravada junto com o pagamento (tabela `envios_finance`) e são mandados em segundo plano. Se o Finance estiver fora do ar, o pagamento acontece normalmente e o envio é repetido com espera crescente, de 10 s até 1 h, em até 15 tentativas.
- Um mesmo pagamento nunca é lançado duas vezes: o Finance recusa repetições pelo `external_id`.
- Se a pessoa gerar outro código ou desconectar no Finance, o Pay percebe no próximo envio e desliga a conexão.

Variáveis: `VALTREA_FINANCE_URL` (vazio desliga a integração) e `VALTREA_CHAVE_CIFRA` (32 bytes em Base64; gere com `openssl rand -base64 32`).

**Para testar com o Finance no computador**, suba o Finance do branch `integracao-valtrea-pay` nas portas 8081 (API) e 3000 (site), para não usar as mesmas portas do Pay. O `compose.yaml` do Pay já aponta para `http://host.docker.internal:8081/api`.

## Publicar a API (Render + Neon)

O [`render.yaml`](render.yaml) já descreve o serviço. A cada push na `main`, o Render publica a versão nova sozinho, e o Flyway atualiza o banco.

1. **Banco:** no [Neon](https://neon.tech), crie o projeto `valtrea-pay` na região **São Paulo**. Em *Connection details*, copie o host, o banco, o usuário e a senha.
2. **Render:** no [painel](https://dashboard.render.com), escolha **New → Blueprint**, selecione o repositório `valtrea-pay` e preencha:
   - `DATABASE_URL`: `jdbc:postgresql://<host do Neon>/<banco>?sslmode=require`
   - `DATABASE_USER` e `DATABASE_PASSWORD`: os do Neon
   - `MAIL_PASSWORD`: uma chave de API do Resend

   O Render gera sozinho o segredo dos tokens e a chave de cifra. A URL do Finance (`https://api.valtrea.com.br/api`) já vem preenchida.
3. **Conferir:** abra `https://<seu-serviço>.onrender.com/actuator/health`. Deve aparecer `"status":"UP"`.
4. **App:** a versão de produção (`release`) usa `https://valtrea-pay-api.onrender.com/`, definido em `android/app/build.gradle.kts`. Para testar no emulador ou no celular contra a API publicada, use a variante **previa**: no Android Studio, abra *Build Variants* e escolha `previa`, ou rode `./gradlew :app:installPrevia`. Ela é igual à de produção, mas assinada com a chave de desenvolvimento, e fica instalada ao lado da versão debug.

> O plano gratuito do Render desliga o serviço depois de 15 minutos sem uso, e a primeira requisição depois disso demora cerca de 1 minuto. Para manter a API sempre ligada, use um plano pago ou um verificador como o cron-job.org chamando `/actuator/health`, como o Finance já faz.

## O que mudou em relação ao projeto original

**Segurança e dinheiro**
- Quem paga vem do token, não do corpo da requisição. No original, qualquer pessoa logada podia pagar em nome de outra.
- O saldo é conferido antes do débito, e débito e crédito acontecem na mesma transação de banco, com as duas contas travadas. No original, o saldo era atualizado em segundo plano, sem conferência, e podia ficar negativo.
- Valores em `BigDecimal`/`NUMERIC(19,2)` em vez de `Double`, para não perder centavos.
- `Idempotency-Key`: se a internet cair e o app reenviar o pagamento, ele não é cobrado duas vezes.
- A busca de contatos não devolve CPF, telefone nem saldo de outras pessoas, e mascara o e-mail. No original, `/usuarios/contatos` listava todos os usuários com CPF.
- Login bloqueado por 15 minutos após 5 senhas erradas, com aviso por e-mail; resposta igual para e-mail inexistente e senha errada.
- Segredo do JWT via variável de ambiente (no original, estava no repositório). Token de acesso de 15 minutos, renovado sozinho pelo app com um token de sessão de 30 dias. Cada token de sessão vale uma vez: se um token já usado reaparecer (sinal de cópia), todas as sessões da conta caem.
- Só envia pagamentos quem confirmou o e-mail. Códigos de 6 números valem 15 minutos e 5 tentativas, e ficam no banco só como HMAC; tokens de sessão, só como SHA-256.
- Banco versionado com Flyway (`ddl-auto: validate`) em vez de `ddl-auto: update`.
- O app só usa HTTPS na versão de produção, e o token não vai para o backup do Android.

**Removido por enquanto:** o cadastro de cartão de crédito. O original salvava número e CVV no próprio banco, o que não pode ser feito fora de um ambiente certificado PCI. O caminho certo é usar um provedor de pagamentos (ex.: Stripe, Pagar.me, Mercado Pago), que guarda o cartão e devolve só um token.

## Próximos passos

- [ ] Entrada de dinheiro real (Pix via provedor de pagamentos) no lugar do depósito de teste
- [x] Renovação automática do token (refresh token) e confirmação de e-mail
- [x] Pagamento por QR Code entre usuários Valtrea (o QR Code Pix depende da integração com o provedor)
- [x] Recuperação de senha
- [x] CI no GitHub Actions rodando os testes da API e o build do app
- [x] Integração com o Valtrea Finance (pagamentos viram transações e o saldo aparece no painel)

## Autor

**João Adolfo** · [Valtrea](https://www.valtrea.com.br) · contato@valtrea.com.br

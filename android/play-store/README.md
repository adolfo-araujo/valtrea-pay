# Publicar o Valtrea Pay no Google Play

Tudo o que o Google pede para o app já está pronto: exclusão de conta no app e na web, política de privacidade, pacote `.aab` assinado, textos e imagens da loja. Este guia mostra o que fica com você, em ordem.

| Arquivo | O que tem |
| --- | --- |
| [`ficha-da-loja.md`](ficha-da-loja.md) | Nome, descrições, categoria, contato e notas da versão para copiar e colar |
| [`formularios.md`](formularios.md) | Respostas de Acesso ao app, Segurança dos dados, Classificação, Público-alvo e Recursos financeiros |
| [`imagens/`](imagens) | Ícone 512×512, destaque 1024×500 e 5 capturas de tela (as fontes `.svg` ficam em `imagens/origem/`) |

Links públicos que o Console pede:
- Política de privacidade: `https://pay.valtrea.com.br/privacidade`
- Exclusão de conta: `https://pay.valtrea.com.br/excluir-conta`

---

## 1. Criar a conta de desenvolvedor (uma vez)

Em [play.google.com/console](https://play.google.com/console), crie uma conta **pessoal** ou de **organização**. A de organização exige CNPJ e número D-U-N-S, mas dispensa o teste fechado obrigatório do passo 5. A taxa é de US$ 25, paga uma vez. O Google confere seus documentos, o que pode levar alguns dias.

## 2. Criar a chave de upload (uma vez, e guardar para sempre)

A chave assina cada versão que você envia. Com o **Play App Signing** (padrão), o Google guarda a chave final do app; se você perder esta chave de upload, dá para pedir outra ao Google, mas é trabalhoso. **Guarde o arquivo e a senha num gerenciador de senhas e numa cópia fora do computador.**

No PowerShell, crie a pasta e a chave (troque a pasta se quiser; ela não deve ficar dentro do projeto):

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\chaves-valtrea"
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore "$env:USERPROFILE\chaves-valtrea\valtrea-pay-upload.jks" -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

O `keytool` pede uma senha (escolha uma forte e anote) e alguns dados: nome, organização (Valtrea), cidade, estado e país (BR).

Depois crie o arquivo `android/keystore.properties` (ele **não vai para o Git**) com este conteúdo, trocando pela sua senha:

```properties
storeFile=C:/Users/jsara/chaves-valtrea/valtrea-pay-upload.jks
storePassword=SUA_SENHA
keyAlias=upload
keyPassword=SUA_SENHA
```

## 3. Gerar o pacote para a loja

Na pasta `android`:

```bash
./gradlew :app:bundleRelease
```

O arquivo fica em `android/app/build/outputs/bundle/release/app-release.aab`. Para cada versão nova enviada à loja, aumente o `versionCode` em `android/app/build.gradle.kts` (1, 2, 3...).

> No Android Studio também dá: **Build → Generate Signed App Bundle / APK → Android App Bundle**, escolhendo a mesma chave.

## 4. Montar o app no Play Console

1. **Criar app:** nome `Valtrea Pay`, idioma Português (Brasil), tipo **App**, **Gratuito**.
2. **Configurar o app** (o Console mostra uma lista): preencha cada item com as respostas de [`formularios.md`](formularios.md). O mais importante é o **Acesso ao app**: crie antes uma conta de revisão no app e informe e-mail e senha.
3. **Página principal da loja:** cole os textos de [`ficha-da-loja.md`](ficha-da-loja.md) e envie as imagens de [`imagens/`](imagens).

## 5. Testar antes de publicar

1. **Teste interno** (até 100 pessoas, sem revisão demorada): em *Testar e lançar → Teste interno*, crie uma versão, envie o `.aab` e adicione o seu e-mail. Use o link de convite para instalar pelo Google Play e conferir se tudo funciona.
2. **Teste fechado** (obrigatório para contas pessoais criadas depois de novembro de 2023): pelo menos **12 testadores** com o app instalado por **14 dias seguidos**. Só depois disso o Console libera pedir acesso à produção.
3. **Produção:** **não publique para o público enquanto o dinheiro for fictício.** Antes, conclua a entrada de dinheiro real por uma instituição autorizada pelo Banco Central e desligue `VALTREA_SANDBOX` no Render (o botão Depositar some sozinho do app).

## Checklist rápido

- [ ] Conta de desenvolvedor aprovada
- [ ] Chave de upload criada, com cópia de segurança
- [ ] `android/keystore.properties` criado
- [ ] `app-release.aab` gerado
- [ ] Conta de revisão criada no app (e-mail confirmado e saldo de teste)
- [ ] Formulários preenchidos ([`formularios.md`](formularios.md))
- [ ] Ficha da loja e imagens enviadas
- [ ] Versão enviada no teste interno e testada pelo Google Play
- [ ] Teste fechado com 12 pessoas por 14 dias

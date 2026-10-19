# Respostas dos formulários do Play Console

Respostas para a versão atual do Valtrea Pay. **Confira antes de enviar**: você responde pelo que declara ao Google. Se algo no app mudar (por exemplo, quando entrar o Pix de verdade), as respostas também mudam.

---

## Acesso ao app (Política → Conteúdo do app → Acesso ao app)

O app exige login, então os revisores do Google precisam de uma conta de teste.

1. Crie no app uma conta só para isso, por exemplo `revisao-play@valtrea.com.br` (um e-mail que você consiga abrir), com um CPF de teste válido.
2. Confirme o e-mail dessa conta e deposite um valor de teste.
3. No Play Console, escolha **"Todas ou algumas funcionalidades estão restritas"** e informe o e-mail, a senha e esta instrução:

```
Entre com o e-mail e a senha acima. A conta já tem e-mail confirmado e saldo de teste. Para testar um pagamento, toque em Pagar e busque por "Valtrea". O saldo é fictício: o botão Depositar adiciona valores de teste.
```

> Crie também uma segunda conta chamada, por exemplo, "Valtrea Recebimento", para o revisor ter para quem pagar.

---

## Segurança dos dados (Política → Conteúdo do app → Segurança dos dados)

**Visão geral**
- O app coleta ou compartilha dados do usuário? **Sim**
- Todos os dados coletados são criptografados em trânsito? **Sim** (só HTTPS)
- Os usuários podem pedir a exclusão dos dados? **Sim**. Link: `https://pay.valtrea.com.br/excluir-conta`

**Tipos de dados coletados**

| Categoria do Play | Dado | Coletado | Compartilhado | Obrigatório | Finalidades |
| --- | --- | --- | --- | --- | --- |
| Informações pessoais → Nome | Nome completo | Sim | Não* | Obrigatório | Funcionalidade do app, Gerenciamento da conta |
| Informações pessoais → Endereço de e-mail | E-mail | Sim | Não* | Obrigatório | Funcionalidade do app, Gerenciamento da conta, Comunicações do desenvolvedor |
| Informações pessoais → Números de telefone | Celular | Sim | Não | Obrigatório | Gerenciamento da conta |
| Informações pessoais → IDs de usuário | CPF | Sim | Não | Obrigatório | Prevenção de fraudes, segurança e conformidade; Gerenciamento da conta |
| Informações financeiras → Histórico de compras / Outras informações financeiras | Transações e saldo da carteira | Sim | Não* | Obrigatório | Funcionalidade do app |

\* O Google não considera "compartilhamento" o envio para prestadores que processam dados em seu nome (Render, Neon, Resend). O envio ao **Valtrea Finance** só acontece quando a pessoa conecta as contas por vontade própria; como é a mesma empresa e por ação do usuário, normalmente não conta como compartilhamento. Se tiver dúvida, marque **Compartilhado** em "Outras informações financeiras" com a finalidade "Funcionalidade do app".

**Não coletados:** localização, contatos, fotos e vídeos (a câmera do QR Code é processada no aparelho pelo Google Play Services), arquivos, áudio, dados de saúde, histórico de navegação, IDs de publicidade.

---

## Classificação do conteúdo (questionário IARC)

- Categoria: **Utilitário, produtividade, comunicação ou outro**
- Violência, conteúdo sexual, linguagem imprópria, drogas: **Não** para tudo
- O app permite que usuários interajam ou troquem conteúdo? **Sim** (a mensagem opcional do pagamento é vista por quem recebe)
- O app compartilha a localização do usuário? **Não**
- O app permite compras de bens digitais? **Não**
- Jogos de azar ou apostas: **Não**

Resultado esperado: **Livre** ou **12 anos**, por causa da interação entre usuários.

---

## Público-alvo

- Faixa etária: **18 anos ou mais** somente
- O app é atraente para crianças? **Não**

---

## Recursos financeiros (Política → Conteúdo do app → Recursos financeiros)

Marque **"Carteira digital / Pagamentos entre pessoas (P2P)"**.

⚠️ **Ponto de atenção.** Para apps de pagamento, o Google pode pedir comprovação de que a empresa pode operar esse serviço no país (no Brasil, autorização do Banco Central ou parceria com uma instituição autorizada). Na fase de testes, com saldo fictício, deixe claro na descrição e nas notas de revisão:

```
Versão de testes: o saldo é fictício (depósitos de teste) e nenhum dinheiro real é movimentado. A entrada de dinheiro real será feita por uma instituição de pagamento autorizada pelo Banco Central antes do lançamento público.
```

Publique **só em teste fechado** enquanto o dinheiro for fictício. Para abrir ao público com dinheiro real, é preciso concluir a integração com uma instituição autorizada (ver a recomendação do Asaas BaaS) e, de preferência, ter a opinião de um advogado de regulação de pagamentos.

---

## Anúncios

- O app contém anúncios? **Não**

## Declaração de governo / notícias / saúde / VPN

- **Não** para todas.

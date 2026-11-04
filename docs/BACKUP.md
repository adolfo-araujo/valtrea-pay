# Backup do banco do Valtrea Pay

## Como funciona

- O workflow `.github/workflows/backup.yml` roda todo dia às 03:20 (horário de Brasília).
- Ele faz uma cópia completa do banco do Neon (`pg_dump`) e criptografa com AES-256 usando a senha do segredo `BACKUP_PASSPHRASE`.
- Os backups ficam na aba **Actions** do GitHub por 30 dias.
- Para rodar na hora: **Actions > Backup diário do banco > Run workflow**.

## Configurar (uma vez)

No GitHub, abra o repositório **valtrea-pay** > **Settings** > **Secrets and variables** > **Actions** > **New repository secret** e crie:

1. `BACKUP_DATABASE_URL`: a string de conexão do Neon no formato do PostgreSQL (não a do Java), por exemplo `postgresql://usuario:senha@host.neon.tech/neondb?sslmode=require`. No Neon: **Dashboard > Connect**, desmarque "Connection pooling" e copie.
2. `BACKUP_PASSPHRASE`: uma senha longa e aleatória (ex.: gerada pelo gerenciador de senhas). **Guarde no gerenciador de senhas**: sem ela, os backups não podem ser abertos.

Depois rode o workflow uma vez pela aba Actions para conferir.

## Como restaurar

1. GitHub > **Actions** > **Backup diário do banco**: abra a execução do dia e baixe o arquivo em **Artifacts**. Extraia o `.zip`.
2. Descriptografe (PowerShell, com o Docker Desktop aberto, na pasta do arquivo):

   ```powershell
   docker run --rm -it -v "${PWD}:/w" -w /w alpine sh -c "apk add -q gnupg && gpg --pinentry-mode loopback -o backup.dump -d valtrea-pay-backup-AAAA-MM-DD.dump.gpg"
   ```

3. **Sempre teste primeiro num banco separado.** No Neon, crie uma **branch** nova e restaure nela:

   ```powershell
   docker run --rm -v "${PWD}:/w" postgres:18 pg_restore --no-owner --no-acl --clean --if-exists -d "COLE_AQUI_A_CONNECTION_STRING_DA_BRANCH" /w/backup.dump
   ```

4. Conferido, aponte as variáveis `DATABASE_URL`, `DATABASE_USER` e `DATABASE_PASSWORD` do Render para a branch, ou restaure no banco principal do mesmo jeito.

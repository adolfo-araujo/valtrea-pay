#!/usr/bin/env bash
# Medições leves e não destrutivas no Valtrea Pay em produção (cerca de 30 pedidos).
# Não cria contas, não grava nada no banco e não envia e-mails.
#
# - Tempo de resposta sem banco (/actuator/health/liveness) e com banco (renovar sessão com um código que não existe,
#   que responde 401 depois de 2 idas e voltas ao banco): a diferença, dividida por 2, é a distância entre o servidor
#   (Render, EUA) e o banco (Neon, São Paulo).
# - Cabeçalhos de segurança da API e das páginas públicas.
# - Se as páginas públicas (política, termos, acessibilidade, exclusão de conta) estão no ar.
set -uo pipefail

PAY=${PAY:-https://pay.valtrea.com.br}
OUT=${OUT:-loadtest/resultado}
mkdir -p "$OUT"
N=10

median() { sort -n | awk '{a[NR]=$1} END {print (NR%2 ? a[(NR+1)/2] : (a[NR/2]+a[NR/2+1])/2)}'; }

# Acorda o servidor (o plano grátis do Render dorme depois de 15 minutos sem uso).
inicio=$(date +%s)
curl -s -o /dev/null --max-time 120 "$PAY/actuator/health"
acordar=$(( $(date +%s) - inicio ))
# Logo depois de acordar, a JVM ainda está aquecendo e as conexões com o banco ainda estão sendo abertas:
# alguns pedidos sem medir, para não confundir isso com a distância até o banco.
for _ in $(seq 8); do
  curl -s -o /dev/null "$PAY/actuator/health/liveness"
  curl -s -o /dev/null -X POST -H 'Content-Type: application/json' -d '{"refreshToken":"aquecimento"}' "$PAY/api/v1/auth/renovar"
done

saude=$(for _ in $(seq $N); do
  curl -s -o /dev/null -w '%{time_starttransfer}\n' "$PAY/actuator/health/liveness"
  sleep 0.3
done | awk '{print $1*1000}' | median)
banco=$(for _ in $(seq $N); do
  curl -s -o /dev/null -w '%{time_starttransfer}\n' -X POST -H 'Content-Type: application/json' \
    -d '{"refreshToken":"sonda-de-latencia-codigo-que-nao-existe"}' "$PAY/api/v1/auth/renovar"
  sleep 0.3
done | awk '{print $1*1000}' | median)
# Renovar com um código inexistente faz 2 idas e voltas ao banco (a busca e o fim da transação), então divide por 2.
db_rtt=$(awk "BEGIN{v=($banco-$saude)/2; print (v<0?0:int(v))}")

hdr() { curl -s -D - -o /dev/null "$@" | tr -d '\r'; }
tem() { echo "$1" | grep -qi "^$2:" && echo "sim" || echo "não"; }
api=$(hdr "$PAY/api/v1/eu")
pagina=$(hdr "$PAY/privacidade")
borda=$(echo "$api" | awk -F': ' 'tolower($1)=="cf-ray"{print $2}')
status_eu=$(echo "$api" | head -1 | awk '{print $2}')

{
  echo "### Sondagem da produção (não destrutiva)"
  echo
  echo "| Medida | Valor |"
  echo "| --- | --- |"
  echo "| Tempo para acordar o servidor (0 = já estava acordado) | ~${acordar} s |"
  echo "| API sem banco (/actuator/health/liveness), mediana de $N | ${saude%.*} ms |"
  echo "| API com 2 idas e voltas ao banco, mediana de $N | ${banco%.*} ms |"
  echo "| Tempo estimado de cada ida e volta API ↔ banco | ~${db_rtt} ms |"
  echo "| Borda da Cloudflare na frente da API (cf-ray) | ${borda:-não encontrada} |"
  echo "| /api/v1/eu sem login responde 401? | $([ "$status_eu" = "401" ] && echo sim || echo "não (HTTP $status_eu)") |"
  echo "| API: HSTS / nosniff / CSP / Referrer-Policy | $(tem "$api" strict-transport-security) / $(tem "$api" x-content-type-options) / $(tem "$api" content-security-policy) / $(tem "$api" referrer-policy) |"
  echo "| Páginas: CSP / X-Frame-Options / Permissions-Policy | $(tem "$pagina" content-security-policy) / $(tem "$pagina" x-frame-options) / $(tem "$pagina" permissions-policy) |"
  for p in /privacidade /termos /acessibilidade /excluir-conta /actuator/health; do
    echo "| Página $PAY$p | HTTP $(curl -s -o /dev/null -w '%{http_code}' "$PAY$p") |"
  done
} > "$OUT/producao.md"

echo "$db_rtt" > "$OUT/db_rtt.txt"
cat "$OUT/producao.md"
[ -n "${GITHUB_STEP_SUMMARY:-}" ] && cat "$OUT/producao.md" >> "$GITHUB_STEP_SUMMARY"
exit 0

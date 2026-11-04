#!/usr/bin/env bash
# Sobe banco + caixa de e-mails falsa + uma ou mais cópias da API (atrás de um balanceador) com os limites de CPU e
# memória escolhidos, cria as contas de teste e roda o k6. Usado pelo CI ("Teste de carga") e pelo
# "Teste de capacidade" (Actions > Run workflow).
#
# Variáveis:
#   USERS          pessoas usando ao mesmo tempo (padrão 100)
#   CPUS / MEMORY  limite de cada cópia da API (ex.: 0.5 e 512m = plano Starter do Render; o Free tem 0.1 CPU)
#   INSTANCES      quantas cópias da API, atrás de um balanceador (padrão 1)
#   DB_LATENCY_MS  atraso extra entre API e banco (a API fica nos EUA e o banco em São Paulo: ~120 ms)
#   DB_POOL        conexões com o banco (HikariCP), por cópia da API
#   RAMP / HOLD    tempo de chegada das pessoas e tempo de uso simultâneo
#   SEED_VUS       cadastros feitos em paralelo no preparo (padrão 10)
#   LABEL          nome do cenário no relatório
set -euo pipefail

USERS=${USERS:-100}
CPUS=${CPUS:-0.5}
MEMORY=${MEMORY:-512m}
INSTANCES=${INSTANCES:-1}
DB_LATENCY_MS=${DB_LATENCY_MS:-0}
DB_POOL=${DB_POOL:-20}
RAMP=${RAMP:-1m}
HOLD=${HOLD:-3m}
SEED_VUS=${SEED_VUS:-10}
LABEL=${LABEL:-"API ${CPUS} CPU"}
OUT=${OUT:-loadtest/resultado}
ROOT=$(cd "$(dirname "$0")/.." && pwd)

mkdir -p "$OUT"
OUT_ABS=$(cd "$OUT" && pwd)

APIS() { seq -f "pay-api%g" 1 "$INSTANCES"; }
cleanup() {
  docker rm -f pay-db pay-mail pay-lb $(APIS) >/dev/null 2>&1 || true
  docker network rm pay-lt >/dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup
docker network create pay-lt >/dev/null

echo "::group::Banco (PostgreSQL 18, 1 CPU) e caixa de e-mails falsa"
docker run -d --name pay-db --network pay-lt --cpus 1 --memory 1g --cap-add NET_ADMIN \
  -e POSTGRES_USER=valtrea -e POSTGRES_PASSWORD=valtrea -e POSTGRES_DB=valtrea_pay postgres:18-alpine \
  -c max_connections=300 >/dev/null
docker run -d --name pay-mail --network pay-lt axllent/mailpit:v1.31.6 >/dev/null
until docker exec pay-db pg_isready -U valtrea -d valtrea_pay >/dev/null 2>&1; do sleep 1; done
sleep 2
if [ "$DB_LATENCY_MS" != "0" ]; then
  docker exec pay-db sh -c "apk add -q iproute2 >/dev/null && tc qdisc add dev eth0 root netem delay ${DB_LATENCY_MS}ms"
  echo "Atraso de ${DB_LATENCY_MS} ms entre a API e o banco"
fi
echo "::endgroup::"

echo "::group::API (imagem Docker do projeto)"
docker build -q -t valtrea-pay-api "$ROOT/api" >/dev/null
# Cada cópia também fica numa porta própria (8081, 8082...) só para conferir se subiu; o k6 usa o balanceador (8080).
for i in $(seq 1 "$INSTANCES"); do
  porta=$((8080 + i))
  docker run -d --name "pay-api$i" --network pay-lt -p "$porta:8080" --cpus "$CPUS" --memory "$MEMORY" \
    -e DATABASE_URL="jdbc:postgresql://pay-db:5432/valtrea_pay" -e DATABASE_USER=valtrea -e DATABASE_PASSWORD=valtrea \
    -e SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="$DB_POOL" \
    -e VALTREA_JWT_SECRET=segredo-do-teste-de-carga-com-32-caracteres \
    -e VALTREA_CHAVE_CIFRA=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY= \
    -e VALTREA_SANDBOX=true -e VALTREA_FINANCE_URL= \
    -e MAIL_HOST=pay-mail -e MAIL_PORT=1025 valtrea-pay-api >/dev/null
  # A primeira cópia cria as tabelas (Flyway); as outras só sobem depois dela.
  for _ in $(seq 1 600); do
    curl -sf "http://localhost:$porta/actuator/health" >/dev/null && break
    sleep 1
  done
  curl -sf "http://localhost:$porta/actuator/health" >/dev/null || { docker logs "pay-api$i" | tail -60; exit 1; }
done

{
  echo "events { worker_connections 8192; }"
  echo "http {"
  echo "  upstream api {"
  echo "    least_conn;"
  for i in $(seq 1 "$INSTANCES"); do echo "    server pay-api$i:8080;"; done
  echo "    keepalive 64;"
  echo "  }"
  echo "  server {"
  echo "    listen 8080;"
  echo "    location / {"
  echo "      proxy_pass http://api;"
  echo "      proxy_http_version 1.1;"
  echo "      proxy_set_header Connection \"\";"
  echo "      proxy_set_header CF-Connecting-IP \$http_cf_connecting_ip;"
  echo "      proxy_read_timeout 90s;"
  echo "    }"
  echo "  }"
  echo "}"
} > "$OUT_ABS/nginx.conf"
docker run -d --name pay-lb --network pay-lt -p 8080:8080 -v "$OUT_ABS/nginx.conf:/etc/nginx/nginx.conf:ro" \
  nginx:1.29-alpine >/dev/null
for _ in $(seq 1 30); do
  curl -sf http://localhost:8080/actuator/health >/dev/null && break
  sleep 1
done
curl -sf http://localhost:8080/actuator/health >/dev/null || { docker logs pay-lb | tail -30; exit 1; }
echo "${INSTANCES} cópia(s) da API no ar, cada uma com ${CPUS} CPU e ${MEMORY}, atrás de um balanceador"
echo "::endgroup::"

echo "::group::Contas de teste"
k6 run --quiet -e USERS="$USERS" -e SEED_VUS="$SEED_VUS" -e API_URL=http://localhost:8080 "$ROOT/loadtest/seed.js"
# Pagar exige e-mail confirmado; aqui confirmamos direto no banco.
docker exec pay-db psql -q -U valtrea -d valtrea_pay -c "UPDATE usuarios SET email_confirmado_em = now() WHERE email LIKE 'carga-%@test.dev'"
echo "::endgroup::"

# Uso de CPU e memória de cada cópia da API durante o teste
( while true; do
    docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' $(APIS) >> "$OUT/stats.csv" 2>/dev/null || true
    sleep 5
  done ) &
STATS_PID=$!

set +e
k6 run --quiet -e USERS="$USERS" -e SEEDED_USERS="$USERS" -e RAMP="$RAMP" -e HOLD="$HOLD" \
  -e API_URL=http://localhost:8080 -e LABEL="$LABEL" -e SUMMARY_FILE="$OUT/resumo.md" \
  "$ROOT/loadtest/usuarios.js"
K6_EXIT=$?
set -e
kill $STATS_PID 2>/dev/null || true

PEAK_CPU=$(awk -F, '{gsub("%","",$2); print $2}' "$OUT/stats.csv" 2>/dev/null | sort -n | tail -1)
PEAK_MEM=$(awk -F, '{split($3,a," "); print a[1]}' "$OUT/stats.csv" 2>/dev/null | sort -h | tail -1)
ESTADO=$(for c in $(APIS); do docker inspect "$c" --format '{{.State.OOMKilled}}/{{.State.Running}}'; done | tr '\n' ' ')
{
  echo "| Pico de CPU de uma cópia da API | ${PEAK_CPU:-—}% (limite: $(awk "BEGIN{print $CPUS*100}")%) |"
  echo "| Pico de memória de uma cópia | ${PEAK_MEM:-—} (limite: ${MEMORY}) |"
  echo "| Cópias: caiu por falta de memória? / no ar? | ${ESTADO} |"
  echo
  echo "_Cenário: ${INSTANCES} × API com ${CPUS} CPU e ${MEMORY}; banco PostgreSQL 18 com 1 CPU; atraso API↔banco de ${DB_LATENCY_MS} ms; ${DB_POOL} conexões com o banco por cópia; ${RAMP} de chegada e ${HOLD} de uso simultâneo._"
} >> "$OUT/resumo.md"

for c in $(APIS); do docker logs "$c" 2>&1; done | grep "\[lento\]" | sed -E 's/ levou [0-9]+ ms.*//' \
  | sed -E 's/.*\[lento\]/[lento]/' | sort | uniq -c | sort -rn | head -10 > "$OUT/rotas-lentas.txt" || true
if [ -s "$OUT/rotas-lentas.txt" ]; then
  { echo; echo "<details><summary>Rotas que passaram de 1 s</summary>"; echo; echo '```'; cat "$OUT/rotas-lentas.txt"; echo '```'; echo "</details>"; } >> "$OUT/resumo.md"
fi
for c in $(APIS); do docker logs "$c" > "$OUT/$c.log" 2>&1 || true; done
cat "$OUT/resumo.md"
[ -n "${GITHUB_STEP_SUMMARY:-}" ] && cat "$OUT/resumo.md" >> "$GITHUB_STEP_SUMMARY"
exit $K6_EXIT

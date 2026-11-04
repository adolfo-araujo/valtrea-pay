// Teste de carga do Valtrea Pay (k6: https://k6.io).
//
// Simula pessoas usando o app ao mesmo tempo. Cada pessoa virtual:
//   1. entra na conta uma vez;
//   2. abre o início (perfil e saldo + extrato, como o app faz);
//   3. "lê" a tela por 5 a 15 segundos;
//   4. às vezes procura alguém e paga R$ 1,00, ou olha mais páginas do extrato;
//   5. repete até o fim do teste.
//
// Uso: k6 run -e USERS=100 -e API_URL=http://localhost:8080 loadtest/usuarios.js
// As contas carga-N@test.dev são criadas antes pelo seed.js.
import http from 'k6/http'
import { check, sleep } from 'k6'
import { emailDe, ipDe, SENHA } from './comum.js'

const API = __ENV.API_URL || 'http://localhost:8080'
const USERS = Number(__ENV.USERS || 100)
const SEEDED = Number(__ENV.SEEDED_USERS || USERS)
const RAMP = __ENV.RAMP || '1m'
const HOLD = __ENV.HOLD || '3m'

export const options = {
    scenarios: {
        uso: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: RAMP, target: USERS },
                { duration: HOLD, target: USERS },
                { duration: '20s', target: 0 },
            ],
            gracefulRampDown: '15s',
        },
    },
    // Mesmo critério do Valtrea Finance: 95% das respostas em menos de 1 s e menos de 1% de erros.
    // O login é mais lento de propósito (a senha é conferida com bcrypt), então tem 2 s.
    thresholds: {
        http_req_failed: ['rate<0.01'],
        'http_req_duration{tipo:leitura}': ['p(95)<1000'],
        'http_req_duration{tipo:escrita}': ['p(95)<1000'],
        'http_req_duration{tipo:login}': ['p(95)<2000'],
        checks: ['rate>0.99'],
    },
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
}

let token = null
const minhaConta = () => (__VU - 1) % SEEDED
const headers = () => ({
    'Content-Type': 'application/json',
    'CF-Connecting-IP': ipDe(__VU + 1000),
    ...(token && { Authorization: `Bearer ${token}` }),
})
const params = (tipo, rota, extra = {}) => ({ headers: { ...headers(), ...extra }, tags: { tipo, rota } })

function entrar() {
    const res = http.post(
        `${API}/api/v1/auth/login`,
        JSON.stringify({ email: emailDe(minhaConta()), senha: SENHA }),
        params('login', 'POST /auth/login'),
    )
    token = check(res, { 'login 200': (r) => r.status === 200 }) ? res.json('accessToken') : null
}

function abrirInicio() {
    const respostas = http.batch([
        ['GET', `${API}/api/v1/eu`, null, params('leitura', 'GET /eu')],
        ['GET', `${API}/api/v1/transacoes?pagina=0&tamanho=20`, null, params('leitura', 'GET /transacoes')],
    ])
    check(respostas, { 'início carregou': (rs) => rs.every((r) => r.status === 200) })
}

function pagarAlguem() {
    const busca = http.get(`${API}/api/v1/contatos?busca=Carga`, params('leitura', 'GET /contatos'))
    check(busca, { 'busca 200': (r) => r.status === 200 })
    let destino = null
    try {
        const contatos = busca.json()
        if (Array.isArray(contatos) && contatos.length > 0) {
            destino = contatos[Math.floor(Math.random() * contatos.length)].id
        }
    } catch (e) {
        destino = null
    }
    if (!destino) return
    const pagamento = http.post(
        `${API}/api/v1/transferencias`,
        JSON.stringify({ destinoId: destino, valor: 1.0, descricao: 'Teste de carga' }),
        params('escrita', 'POST /transferencias', { 'Idempotency-Key': `carga-${__VU}-${__ITER}-${Date.now()}` }),
    )
    check(pagamento, { 'pagamento 201': (r) => r.status === 201 })
}

export default function () {
    if (!token) {
        entrar()
        if (!token) {
            sleep(5)
            return
        }
    }

    abrirInicio()
    sleep(5 + Math.random() * 10)

    const sorteio = Math.random()
    if (sorteio < 0.4) {
        pagarAlguem()
        abrirInicio() // o app atualiza saldo e extrato depois de pagar
    } else if (sorteio < 0.55) {
        const res = http.get(`${API}/api/v1/transacoes?pagina=1&tamanho=20`, params('leitura', 'GET /transacoes'))
        check(res, { 'extrato 200': (r) => r.status === 200 })
    }
    sleep(5 + Math.random() * 10)
}

export function handleSummary(data) {
    const m = data.metrics
    const ms = (metric, stat) => (metric?.values?.[stat] != null ? Math.round(metric.values[stat]) : '—')
    const pct = (v) => (v == null ? '—' : `${(v * 100).toFixed(2)}%`)
    const linha = (nome, chave) => [
        `${nome}: mediana / p95 / p99 (ms)`,
        `${ms(m[chave], 'med')} / ${ms(m[chave], 'p(95)')} / ${ms(m[chave], 'p(99)')}`,
    ]
    const rows = [
        ['Requisições', m.http_reqs?.values?.count ?? 0],
        ['Requisições por segundo', (m.http_reqs?.values?.rate ?? 0).toFixed(1)],
        ['Erros', pct(m.http_req_failed?.values?.rate)],
        linha('Leitura', 'http_req_duration{tipo:leitura}'),
        linha('Pagamentos', 'http_req_duration{tipo:escrita}'),
        linha('Login', 'http_req_duration{tipo:login}'),
    ]
    const reprovados = Object.entries(m)
        .filter(([, v]) => v.thresholds && Object.values(v.thresholds).some((t) => !t.ok))
        .map(([k]) => k)
    const veredito = reprovados.length === 0 ? 'APROVADO' : `REPROVADO (${reprovados.join(', ')})`
    const titulo = `${__ENV.LABEL || 'Teste de carga'}: ${USERS} pessoas ao mesmo tempo`
    const md = `### ${titulo} — ${veredito}\n\n| Medida | Valor |\n| --- | --- |\n` +
        rows.map(([k, v]) => `| ${k} | ${v} |`).join('\n') + '\n'
    return { stdout: `\n${md}\n`, [__ENV.SUMMARY_FILE || 'resumo.md']: md }
}

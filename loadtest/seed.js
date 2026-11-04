// Prepara o teste de carga: cria as contas carga-N@test.dev e põe saldo de teste em cada uma.
// Uso: k6 run -e USERS=100 -e API_URL=http://localhost:8080 loadtest/seed.js
// Depois o run.sh marca os e-mails como confirmados direto no banco (pagar exige e-mail confirmado).
// Os cadastros são feitos em paralelo (SEED_VUS de cada vez): com 1000 contas e 0,1 CPU, um por vez levaria
// mais de 15 minutos só para conferir as senhas (bcrypt).
import http from 'k6/http'
import exec from 'k6/execution'
import { check } from 'k6'
import { cpfDe, emailDe, ipDe, SENHA } from './comum.js'

const API = __ENV.API_URL || 'http://localhost:8080'
const USERS = Number(__ENV.USERS || 100)
const SEED_VUS = Number(__ENV.SEED_VUS || 10)

export const options = {
    scenarios: {
        preparo: {
            executor: 'shared-iterations',
            vus: Math.min(SEED_VUS, USERS),
            iterations: USERS,
            maxDuration: '40m',
        },
    },
    // Preparo não é medido: só precisa terminar sem erro.
    thresholds: { checks: ['rate>0.99'] },
}

export default function () {
    const n = exec.scenario.iterationInTest
    const headers = { 'Content-Type': 'application/json', 'CF-Connecting-IP': ipDe(n + 1) }
    const cadastro = http.post(
        `${API}/api/v1/auth/cadastro`,
        JSON.stringify({ nome: `Carga Pessoa ${n}`, email: emailDe(n), cpf: cpfDe(n), senha: SENHA }),
        { headers, timeout: '120s' },
    )
    if (!check(cadastro, { 'cadastro 201': (r) => r.status === 201 })) {
        console.error(`cadastro ${n}: ${cadastro.status} ${cadastro.body}`)
        return
    }
    const token = cadastro.json('accessToken')
    const deposito = http.post(`${API}/api/v1/depositos`, JSON.stringify({ valor: 1000.0 }), {
        headers: { ...headers, Authorization: `Bearer ${token}` },
        timeout: '120s',
    })
    check(deposito, { 'depósito 201': (r) => r.status === 201 })
}

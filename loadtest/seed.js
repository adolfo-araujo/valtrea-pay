// Prepara o teste de carga: cria as contas carga-N@test.dev e põe saldo de teste em cada uma.
// Uso: k6 run -e USERS=100 -e API_URL=http://localhost:8080 loadtest/seed.js
// Depois o run.sh marca os e-mails como confirmados direto no banco (pagar exige e-mail confirmado).
import http from 'k6/http'
import { check } from 'k6'
import { cpfDe, emailDe, ipDe, SENHA } from './comum.js'

const API = __ENV.API_URL || 'http://localhost:8080'
const USERS = Number(__ENV.USERS || 100)

export const options = { vus: 1, iterations: 1, setupTimeout: '10m' }

export default function () {
    let criadas = 0
    for (let n = 0; n < USERS; n++) {
        const headers = { 'Content-Type': 'application/json', 'CF-Connecting-IP': ipDe(n + 1) }
        const cadastro = http.post(
            `${API}/api/v1/auth/cadastro`,
            JSON.stringify({ nome: `Carga Pessoa ${n}`, email: emailDe(n), cpf: cpfDe(n), senha: SENHA }),
            { headers },
        )
        if (!check(cadastro, { 'cadastro 201': (r) => r.status === 201 })) {
            console.error(`cadastro ${n}: ${cadastro.status} ${cadastro.body}`)
            continue
        }
        const token = cadastro.json('accessToken')
        const deposito = http.post(`${API}/api/v1/depositos`, JSON.stringify({ valor: 1000.0 }), {
            headers: { ...headers, Authorization: `Bearer ${token}` },
        })
        check(deposito, { 'depósito 201': (r) => r.status === 201 })
        criadas++
    }
    console.log(`Contas de carga criadas: ${criadas} de ${USERS}`)
}

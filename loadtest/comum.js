// Funções usadas pelo preparo (seed.js) e pelo teste (usuarios.js).

// CPF válido e sempre o mesmo para a mesma pessoa de teste (o cadastro exige CPF válido e único).
export function cpfDe(n) {
    const nove = String(100000000 + n * 7919).slice(-9)
    const digito = (parcial, peso) => {
        let soma = 0
        for (let i = 0; i < parcial.length; i++) soma += Number(parcial[i]) * (peso - i)
        const resto = (soma * 10) % 11
        return resto === 10 ? 0 : resto
    }
    const d1 = digito(nove, 10)
    const d2 = digito(nove + d1, 11)
    return nove + d1 + d2
}

export const emailDe = (n) => `carga-${n}@test.dev`
export const SENHA = 'senha-de-carga-123'

// Cada pessoa virtual tem um IP simulado, como na vida real: o limite de pedidos da API é por IP
// (em produção esse cabeçalho vem da Cloudflare).
export const ipDe = (n) => `10.${(n >> 16) & 255}.${(n >> 8) & 255}.${n & 255}`

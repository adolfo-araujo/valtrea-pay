package br.com.valtrea.pay.data

import java.math.BigDecimal
import java.net.URI
import java.util.UUID

/**
 * Conteúdo do QR Code e do link de pagamento: `valtrea://pagar/<id de quem recebe>?valor=12.34`.
 * O valor é opcional. Só funciona entre usuários do Valtrea (não é um QR Code Pix).
 */
data class LinkPagamento(val destinoId: String, val valor: BigDecimal? = null) {

    fun paraTexto(): String =
        "valtrea://pagar/$destinoId" + (valor?.let { "?valor=${it.setScale(2).toPlainString()}" } ?: "")

    companion object {
        /** Devolve `null` para qualquer texto que não seja um link de pagamento Valtrea válido. */
        fun ler(texto: String): LinkPagamento? {
            val uri = runCatching { URI(texto.trim()) }.getOrNull() ?: return null
            if (uri.scheme != "valtrea" || uri.host != "pagar") return null
            val id = uri.path?.trim('/')?.takeIf { it.isNotEmpty() } ?: return null
            if (runCatching { UUID.fromString(id) }.isFailure) return null
            val valor = uri.query?.split('&')
                ?.firstOrNull { it.startsWith("valor=") }
                ?.removePrefix("valor=")
                ?.toBigDecimalOrNull()
                ?.takeIf { it.signum() > 0 && it.scale() <= 2 }
            return LinkPagamento(id, valor)
        }
    }
}

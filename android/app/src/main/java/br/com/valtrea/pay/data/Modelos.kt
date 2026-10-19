package br.com.valtrea.pay.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

// Espelham os DTOs da API (api/src/main/java/br/com/valtrea/pay). Dinheiro é sempre BigDecimal, nunca Double.

@Serializable
data class CadastroRequest(
    val nome: String,
    val email: String,
    val cpf: String,
    val telefone: String,
    val senha: String,
)

@Serializable
data class LoginRequest(val email: String, val senha: String)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val tipo: String,
    val expiraEm: String,
    val refreshToken: String,
)

@Serializable
data class RenovarRequest(val refreshToken: String)

@Serializable
data class CodigoRequest(val codigo: String)

@Serializable
data class EsqueciSenhaRequest(val email: String)

@Serializable
data class RedefinirSenhaRequest(val email: String, val codigo: String, val novaSenha: String)

@Serializable
data class Perfil(
    val id: String,
    val nome: String,
    val email: String,
    val telefone: String,
    @Serializable(with = BigDecimalSerializer::class) val saldo: BigDecimal,
    val emailConfirmado: Boolean,
)

@Serializable
data class AtualizarPerfilRequest(val nome: String, val telefone: String)

@Serializable
data class StatusFinance(val disponivel: Boolean, val conectado: Boolean, val conectadoEm: String? = null)

@Serializable
data class ConectarFinanceRequest(val codigo: String)

@Serializable
data class Contato(val id: String, val nome: String, val emailMascarado: String)

@Serializable
data class TransferenciaRequest(
    val destinoId: String,
    @Serializable(with = BigDecimalSerializer::class) val valor: BigDecimal,
    val descricao: String? = null,
)

@Serializable
data class DepositoRequest(@Serializable(with = BigDecimalSerializer::class) val valor: BigDecimal)

@Serializable
enum class Direcao { ENTRADA, SAIDA }

@Serializable
enum class TipoTransacao { TRANSFERENCIA, DEPOSITO }

@Serializable
data class Contraparte(val id: String, val nome: String)

@Serializable
data class Transacao(
    val id: String,
    val tipo: TipoTransacao,
    val direcao: Direcao,
    @Serializable(with = BigDecimalSerializer::class) val valor: BigDecimal,
    val descricao: String? = null,
    val contraparte: Contraparte? = null,
    val criadoEm: String,
)

@Serializable
data class Pagina<T>(
    val itens: List<T>,
    val pagina: Int,
    val tamanho: Int,
    val totalItens: Long,
    val totalPaginas: Int,
)

/** Resposta de erro da API no formato ProblemDetail. */
@Serializable
data class Problema(val detail: String? = null, val campos: Map<String, String>? = null)

/** Lê e escreve números JSON sem passar por Double, para não perder centavos. */
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BigDecimal =
        if (decoder is JsonDecoder) BigDecimal(decoder.decodeJsonElement().jsonPrimitive.content)
        else BigDecimal(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        if (encoder is JsonEncoder) encoder.encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
        else encoder.encodeString(value.toPlainString())
    }
}

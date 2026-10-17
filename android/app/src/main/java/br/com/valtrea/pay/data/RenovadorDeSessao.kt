package br.com.valtrea.pay.data

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException

/** Chamada de renovação, num cliente separado para não entrar em loop com o próprio renovador. */
interface RenovacaoApi {
    @POST("api/v1/auth/renovar")
    fun renovar(@Body pedido: RenovarRequest): Call<TokenResponse>
}

/**
 * Quando a API responde 401 porque o token de acesso venceu, troca o token de sessão por um par novo e repete a
 * requisição, sem a pessoa perceber. Se a sessão não vale mais, encerra e o app volta para a tela de login.
 */
class RenovadorDeSessao(
    private val sessao: Sessao,
    private val renovacao: RenovacaoApi,
) : Authenticator {

    private val trava = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Já tentamos com um token renovado e ainda deu 401: desiste.
        if (response.priorResponse != null) return null
        val tokenUsado = response.request.header("Authorization")?.removePrefix("Bearer ") ?: return null

        synchronized(trava) {
            // Outra requisição pode ter renovado enquanto esta esperava: só usa o token novo.
            val atual = sessao.tokenAcesso
            if (atual != null && atual != tokenUsado) return comToken(response.request, atual)

            val tokenSessao = sessao.tokenSessao ?: return null
            val resposta = try {
                renovacao.renovar(RenovarRequest(tokenSessao)).execute()
            } catch (_: IOException) {
                return null // Sem rede: mantém a sessão para tentar de novo depois.
            }
            val novo = resposta.body()
            if (!resposta.isSuccessful || novo == null) {
                if (resposta.code() == 401) sessao.encerrar()
                return null
            }
            sessao.salvarAgora(novo)
            return comToken(response.request, novo.accessToken)
        }
    }

    private fun comToken(pedido: Request, token: String) =
        pedido.newBuilder().header("Authorization", "Bearer $token").build()
}

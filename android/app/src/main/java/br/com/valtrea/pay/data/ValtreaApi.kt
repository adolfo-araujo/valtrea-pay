package br.com.valtrea.pay.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ValtreaApi {

    @POST("api/v1/auth/cadastro")
    suspend fun cadastrar(@Body pedido: CadastroRequest): TokenResponse

    @POST("api/v1/auth/login")
    suspend fun entrar(@Body pedido: LoginRequest): TokenResponse

    @POST("api/v1/auth/sair")
    suspend fun sair(@Body pedido: RenovarRequest)

    @POST("api/v1/auth/confirmar-email")
    suspend fun confirmarEmail(@Body pedido: CodigoRequest)

    @POST("api/v1/auth/reenviar-confirmacao")
    suspend fun reenviarConfirmacao()

    @POST("api/v1/auth/esqueci-senha")
    suspend fun esqueciSenha(@Body pedido: EsqueciSenhaRequest)

    @POST("api/v1/auth/redefinir-senha")
    suspend fun redefinirSenha(@Body pedido: RedefinirSenhaRequest)

    @GET("api/v1/eu")
    suspend fun perfil(): Perfil

    @GET("api/v1/contatos")
    suspend fun contatos(@Query("busca") busca: String): List<Contato>

    @GET("api/v1/contatos/{id}")
    suspend fun contato(@Path("id") id: String): Contato

    @POST("api/v1/transferencias")
    suspend fun transferir(
        @Header("Idempotency-Key") chave: String,
        @Body pedido: TransferenciaRequest,
    ): Transacao

    @POST("api/v1/depositos")
    suspend fun depositar(@Body pedido: DepositoRequest): Transacao

    @GET("api/v1/transacoes")
    suspend fun extrato(
        @Query("pagina") pagina: Int = 0,
        @Query("tamanho") tamanho: Int = 20,
    ): Pagina<Transacao>
}

package br.com.valtrea.pay

import android.app.Application
import br.com.valtrea.pay.data.Sessao
import br.com.valtrea.pay.data.ValtreaApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Cria e guarda as dependências do app (injeção manual, sem framework). */
class ValtreaApp : Application() {

    lateinit var sessao: Sessao
        private set
    lateinit var api: ValtreaApi
        private set

    override fun onCreate() {
        super.onCreate()
        sessao = Sessao(this, CoroutineScope(SupervisorJob()))
        api = criarApi(sessao)
    }

    private fun criarApi(sessao: Sessao): ValtreaApi {
        val cliente = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = sessao.tokenAtual
                val pedido = if (token != null) {
                    chain.request().newBuilder().header("Authorization", "Bearer $token").build()
                } else {
                    chain.request()
                }
                val resposta = chain.proceed(pedido)
                // Token vencido ou revogado: volta para a tela de login.
                if (resposta.code == 401 && token != null) sessao.encerrar()
                resposta
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_URL)
            .client(cliente)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ValtreaApi::class.java)
    }
}

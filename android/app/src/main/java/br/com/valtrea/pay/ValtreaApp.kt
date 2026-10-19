package br.com.valtrea.pay

import android.app.Application
import br.com.valtrea.pay.data.RenovacaoApi
import br.com.valtrea.pay.data.RenovadorDeSessao
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

        val base = OkHttpClient.Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            // O plano gratuito do Render leva até ~1 minuto para acordar o servidor; esperar menos que isso
            // faz a primeira ação do dia falhar.
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(75, TimeUnit.SECONDS)
            .build()

        val renovacao = retrofit(base).create(RenovacaoApi::class.java)
        val comSessao = base.newBuilder()
            .addInterceptor { chain ->
                val token = sessao.tokenAcesso
                val pedido = chain.request()
                chain.proceed(
                    if (token != null) pedido.newBuilder().header("Authorization", "Bearer $token").build()
                    else pedido,
                )
            }
            .authenticator(RenovadorDeSessao(sessao, renovacao))
            .build()
        api = retrofit(comSessao).create(ValtreaApi::class.java)
    }

    private fun retrofit(cliente: OkHttpClient): Retrofit {
        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_URL)
            .client(cliente)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}

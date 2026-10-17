package br.com.valtrea.pay.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

private val Context.dataStore by preferencesDataStore(name = "sessao")

sealed interface EstadoSessao {
    data object Carregando : EstadoSessao
    data object Deslogado : EstadoSessao
    data class Logado(val token: String) : EstadoSessao
}

/** Guarda o token de acesso no armazenamento privado do app e avisa a interface quando ele muda. */
class Sessao(private val context: Context, private val escopo: CoroutineScope) {

    private val chaveToken = stringPreferencesKey("token")
    private val chaveExpiracao = stringPreferencesKey("expira_em")

    val estado: StateFlow<EstadoSessao> = context.dataStore.data
        .map { prefs ->
            val token = prefs[chaveToken]
            val expiraEm = prefs[chaveExpiracao]?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (token != null && expiraEm != null && expiraEm.isAfter(Instant.now())) {
                EstadoSessao.Logado(token)
            } else {
                EstadoSessao.Deslogado
            }
        }
        .stateIn(escopo, SharingStarted.Eagerly, EstadoSessao.Carregando)

    /** Lido pelo interceptor do OkHttp a cada requisição. */
    val tokenAtual: String?
        get() = (estado.value as? EstadoSessao.Logado)?.token

    suspend fun salvar(token: TokenResponse) {
        context.dataStore.edit {
            it[chaveToken] = token.accessToken
            it[chaveExpiracao] = token.expiraEm
        }
    }

    fun encerrar() {
        escopo.launch { context.dataStore.edit { it.clear() } }
    }
}

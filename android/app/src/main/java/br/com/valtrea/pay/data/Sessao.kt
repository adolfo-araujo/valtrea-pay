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
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "sessao")

sealed interface EstadoSessao {
    data object Carregando : EstadoSessao
    data object Deslogado : EstadoSessao
    data object Logado : EstadoSessao
}

/**
 * Guarda os tokens no armazenamento privado do app. A pessoa continua logada enquanto houver um token de sessão
 * (refreshToken); o token de acesso dura pouco e é renovado sozinho pelo [RenovadorDeSessao].
 */
class Sessao(private val context: Context, private val escopo: CoroutineScope) {

    private val chaveAcesso = stringPreferencesKey("access_token")
    private val chaveSessao = stringPreferencesKey("refresh_token")

    /** Cópia em memória, lida pelo OkHttp a cada requisição sem esperar o disco. */
    @Volatile var tokenAcesso: String? = null
        private set
    @Volatile var tokenSessao: String? = null
        private set

    val estado: StateFlow<EstadoSessao> = context.dataStore.data
        .map { prefs ->
            tokenAcesso = prefs[chaveAcesso]
            tokenSessao = prefs[chaveSessao]
            if (tokenSessao != null) EstadoSessao.Logado else EstadoSessao.Deslogado
        }
        .stateIn(escopo, SharingStarted.Eagerly, EstadoSessao.Carregando)

    suspend fun salvar(token: TokenResponse) {
        tokenAcesso = token.accessToken
        tokenSessao = token.refreshToken
        context.dataStore.edit {
            it[chaveAcesso] = token.accessToken
            it[chaveSessao] = token.refreshToken
        }
    }

    /** Para quem já está numa thread de rede do OkHttp e não pode suspender. */
    fun salvarAgora(token: TokenResponse) = runBlocking { salvar(token) }

    fun encerrar() {
        tokenAcesso = null
        tokenSessao = null
        escopo.launch { context.dataStore.edit { it.clear() } }
    }
}

package br.com.valtrea.pay.data

import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

private val json = Json { ignoreUnknownKeys = true }

/** Transforma qualquer falha de rede ou da API numa frase que dá para mostrar ao usuário. */
fun Throwable.mensagemAmigavel(): String = when (this) {
    is HttpException -> {
        val corpo = response()?.errorBody()?.string()
        val problema = corpo?.let { runCatching { json.decodeFromString<Problema>(it) }.getOrNull() }
        problema?.campos?.values?.firstOrNull()
            ?: problema?.detail
            ?: "Algo deu errado (${code()}). Tente novamente."
    }
    // O servidor gratuito "dorme" sem uso e leva até um minuto para acordar.
    is SocketTimeoutException -> "O servidor está demorando para responder. Tente de novo em alguns segundos."
    is IOException -> "Sem conexão com o servidor. Verifique a internet."
    else -> "Algo deu errado. Tente novamente."
}

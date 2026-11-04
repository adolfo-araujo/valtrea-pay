package br.com.valtrea.pay.ui.componentes

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import br.com.valtrea.pay.BuildConfig

/** Páginas públicas servidas pela API (exigidas pelo Google Play). */
object Links {
    /** Versão vigente dos Termos e da Política. Igual a Termos.VERSAO na API. */
    const val VERSAO_TERMOS = "2026-11-04"

    val privacidade = BuildConfig.API_URL + "privacidade"
    val termos = BuildConfig.API_URL + "termos"
    val acessibilidade = BuildConfig.API_URL + "acessibilidade"

    fun abrir(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}

package br.com.valtrea.pay.ui.componentes

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import br.com.valtrea.pay.BuildConfig

/** Páginas públicas servidas pela API (exigidas pelo Google Play). */
object Links {
    val privacidade = BuildConfig.API_URL + "privacidade"

    fun abrir(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}

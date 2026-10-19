package br.com.valtrea.pay.ui.componentes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Seta de voltar para as telas que abrem por cima de uma aba (Receber, Confirmar e-mail). */
@Composable
fun BotaoVoltar(aoVoltar: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = aoVoltar, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
    }
}

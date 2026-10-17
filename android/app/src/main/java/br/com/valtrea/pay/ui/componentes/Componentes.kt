package br.com.valtrea.pay.ui.componentes

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.valtrea.pay.R
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val localeBr = Locale.forLanguageTag("pt-BR")
private val formatoData = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", localeBr)

fun BigDecimal.emReais(): String = NumberFormat.getCurrencyInstance(localeBr).format(this)

fun String.dataCurta(): String = runCatching {
    formatoData.format(Instant.parse(this).atZone(ZoneId.systemDefault()))
}.getOrDefault("")

/** Logo horizontal: o "V" e o nome Valtrea, como no guia de marca. */
@Composable
fun LogoValtrea(modifier: Modifier = Modifier, tamanhoIcone: Dp = 40.dp) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(tamanhoIcone * 1.6f),
        )
        Text(
            "Valtrea",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

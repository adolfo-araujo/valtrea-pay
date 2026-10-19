package br.com.valtrea.pay.ui.receber

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.valtrea.pay.data.LinkPagamento
import br.com.valtrea.pay.data.Perfil
import br.com.valtrea.pay.data.ValtreaApi
import br.com.valtrea.pay.data.mensagemAmigavel
import br.com.valtrea.pay.ui.componentes.BotaoVoltar
import br.com.valtrea.pay.ui.componentes.emReais
import br.com.valtrea.pay.ui.theme.Valtrea
import br.com.valtrea.pay.ui.valtreaViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.launch

class ReceberViewModel(private val api: ValtreaApi) : ViewModel() {

    var perfil by mutableStateOf<Perfil?>(null)
        private set
    var erro by mutableStateOf<String?>(null)
        private set
    var valorTexto by mutableStateOf("")

    init {
        viewModelScope.launch {
            runCatching { api.perfil() }
                .onSuccess { perfil = it }
                .onFailure { erro = it.mensagemAmigavel() }
        }
    }

    val valor get() = valorTexto.replace(".", "").replace(',', '.').toBigDecimalOrNull()
        ?.takeIf { it.signum() > 0 && it.scale() <= 2 }

    val link get() = perfil?.let { LinkPagamento(it.id, valor) }
}

@Composable
fun ReceberScreen(aoVoltar: () -> Unit, contentPadding: PaddingValues) {
    val vm = valtreaViewModel { ReceberViewModel(it.api) }
    val contexto = LocalContext.current
    val link = vm.link

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BotaoVoltar(aoVoltar, Modifier.align(Alignment.Start).offset(x = (-12).dp))
        Text("Receber", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Mostre este código para quem vai te pagar. A pessoa lê o código na aba Pagar do app Valtrea.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (link != null) {
            // Fundo sempre branco: leitores de QR Code precisam de contraste, mesmo no modo escuro.
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Valtrea.Branco)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QrCode(link.paraTexto(), Modifier.fillMaxWidth().aspectRatio(1f))
                Text(vm.perfil?.nome.orEmpty(), color = Valtrea.Grafite, fontWeight = FontWeight.SemiBold)
                link.valor?.let {
                    Text(it.emReais(), color = Valtrea.VerdeSecundario, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                }
            }
        }

        OutlinedTextField(
            value = vm.valorTexto,
            onValueChange = { vm.valorTexto = it },
            label = { Text("Valor (opcional)") },
            placeholder = { Text("Quem paga escolhe o valor") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                val texto = "Me pague pelo Valtrea: ${link!!.paraTexto()}"
                contexto.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto),
                        "Compartilhar link de pagamento",
                    ),
                )
            },
            enabled = link != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Default.Share, contentDescription = null, Modifier.size(18.dp))
            Text("  Compartilhar link")
        }
    }
}

/** Desenha o QR Code módulo a módulo. Recalcula só quando o texto muda. */
@Composable
private fun QrCode(texto: String, modifier: Modifier) {
    val matriz = remember(texto) { gerarMatriz(texto) }
    Canvas(modifier) {
        val modulo = size.minDimension / matriz.width
        for (y in 0 until matriz.height) {
            for (x in 0 until matriz.width) {
                if (matriz[x, y]) {
                    // Meio pixel a mais evita frestas claras entre módulos vizinhos.
                    drawRect(Valtrea.Grafite, topLeft = Offset(x * modulo, y * modulo),
                        size = Size(modulo + 0.5f, modulo + 0.5f))
                }
            }
        }
    }
}

private fun gerarMatriz(texto: String): BitMatrix = QRCodeWriter().encode(
    texto,
    BarcodeFormat.QR_CODE,
    0,
    0,
    mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
)

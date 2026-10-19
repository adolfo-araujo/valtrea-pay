package br.com.valtrea.pay.ui.confirmar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.valtrea.pay.data.CodigoRequest
import br.com.valtrea.pay.data.ValtreaApi
import br.com.valtrea.pay.data.mensagemAmigavel
import br.com.valtrea.pay.ui.componentes.BotaoVoltar
import br.com.valtrea.pay.ui.theme.Valtrea
import br.com.valtrea.pay.ui.valtreaViewModel
import kotlinx.coroutines.launch

class ConfirmarEmailViewModel(private val api: ValtreaApi) : ViewModel() {

    var email by mutableStateOf("")
        private set
    var codigo by mutableStateOf("")
        private set
    var enviando by mutableStateOf(false)
        private set
    var confirmado by mutableStateOf(false)
        private set
    var erro by mutableStateOf<String?>(null)
        private set
    var aviso by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { runCatching { api.perfil() }.onSuccess { email = it.email } }
    }

    fun aoMudarCodigo(texto: String) {
        codigo = texto.filter(Char::isDigit).take(6)
    }

    fun confirmar() {
        if (enviando || codigo.length != 6) return
        enviando = true
        erro = null
        aviso = null
        viewModelScope.launch {
            runCatching { api.confirmarEmail(CodigoRequest(codigo)) }
                .onSuccess { confirmado = true }
                .onFailure { erro = it.mensagemAmigavel() }
            enviando = false
        }
    }

    fun reenviar() {
        erro = null
        aviso = null
        viewModelScope.launch {
            runCatching { api.reenviarConfirmacao() }
                .onSuccess { aviso = "Enviamos um código novo." }
                .onFailure { erro = it.mensagemAmigavel() }
        }
    }
}

@Composable
fun ConfirmarEmailScreen(aoConcluir: () -> Unit, aoVoltar: () -> Unit, contentPadding: PaddingValues) {
    val vm = valtreaViewModel { ConfirmarEmailViewModel(it.api) }
    LaunchedEffect(vm.confirmado) { if (vm.confirmado) aoConcluir() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BotaoVoltar(aoVoltar, Modifier.align(Alignment.Start).offset(x = (-12).dp))
        Icon(
            Icons.Default.MarkEmailRead,
            contentDescription = null,
            tint = Valtrea.VerdePrincipal,
            modifier = Modifier.size(64.dp).padding(top = 8.dp),
        )
        Text("Confirme seu e-mail", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (vm.email.isBlank()) "Digite o código de 6 números que enviamos para o seu e-mail."
            else "Digite o código de 6 números que enviamos para ${vm.email}.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = vm.codigo,
            onValueChange = vm::aoMudarCodigo,
            label = { Text("Código") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )
        vm.aviso?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        Button(
            onClick = vm::confirmar,
            enabled = vm.codigo.length == 6 && !vm.enviando,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (vm.enviando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Confirmar")
        }
        TextButton(onClick = vm::reenviar) { Text("Não recebi, enviar outro código") }
    }
}

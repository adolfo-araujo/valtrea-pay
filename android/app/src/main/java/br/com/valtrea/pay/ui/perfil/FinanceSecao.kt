package br.com.valtrea.pay.ui.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.valtrea.pay.data.ConectarFinanceRequest
import br.com.valtrea.pay.data.StatusFinance
import br.com.valtrea.pay.data.ValtreaApi
import br.com.valtrea.pay.data.mensagemAmigavel
import br.com.valtrea.pay.ui.theme.Valtrea
import br.com.valtrea.pay.ui.valtreaViewModel
import kotlinx.coroutines.launch

class FinanceViewModel(private val api: ValtreaApi) : ViewModel() {

    var status by mutableStateOf<StatusFinance?>(null)
        private set
    var codigo by mutableStateOf("")
    var enviando by mutableStateOf(false)
        private set
    var erro by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { runCatching { api.statusFinance() }.onSuccess { status = it } }
    }

    fun conectar() = executar {
        status = api.conectarFinance(ConectarFinanceRequest(codigo.trim()))
        codigo = ""
    }

    fun desconectar() = executar { status = api.desconectarFinance() }

    private fun executar(acao: suspend () -> Unit) {
        if (enviando) return
        enviando = true
        erro = null
        viewModelScope.launch {
            runCatching { acao() }.onFailure { erro = it.mensagemAmigavel() }
            enviando = false
        }
    }
}

/** Liga a carteira ao Valtrea Finance: os pagamentos viram transações lá, e o saldo aparece no painel. */
@Composable
fun FinanceSecao() {
    val vm = valtreaViewModel { FinanceViewModel(it.api) }
    val status = vm.status ?: return
    if (!status.disponivel) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Valtrea Finance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (status.conectado) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Valtrea.VerdePrincipal,
                    modifier = Modifier.size(18.dp))
                Text("Conectado", color = Valtrea.VerdePrincipal, fontWeight = FontWeight.SemiBold)
            }
        }

        if (status.conectado) {
            Text(
                "Seus pagamentos entram no Finance sozinhos: enviados como gastos e recebidos como ganhos. " +
                    "O saldo da carteira aparece no painel.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = vm::desconectar, enabled = !vm.enviando, modifier = Modifier.fillMaxWidth()) {
                Text("Desconectar")
            }
        } else {
            Text(
                "No painel do Valtrea Finance, toque em Conectar no cartão Carteira Valtrea Pay, copie o código " +
                    "e cole aqui.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = vm.codigo,
                onValueChange = { vm.codigo = it },
                label = { Text("Código de conexão") },
                placeholder = { Text("vfp_...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = vm::conectar,
                enabled = vm.codigo.isNotBlank() && !vm.enviando,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (vm.enviando) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Link, contentDescription = null, Modifier.size(18.dp))
                    Text("  Conectar")
                }
            }
        }
        vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

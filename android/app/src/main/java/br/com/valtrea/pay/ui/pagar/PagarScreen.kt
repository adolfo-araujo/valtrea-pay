package br.com.valtrea.pay.ui.pagar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.valtrea.pay.data.Contato
import br.com.valtrea.pay.data.Transacao
import br.com.valtrea.pay.data.TransferenciaRequest
import br.com.valtrea.pay.data.ValtreaApi
import br.com.valtrea.pay.data.mensagemAmigavel
import br.com.valtrea.pay.ui.componentes.emReais
import br.com.valtrea.pay.ui.theme.Valtrea
import br.com.valtrea.pay.ui.valtreaViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

class PagarViewModel(private val api: ValtreaApi) : ViewModel() {

    var busca by mutableStateOf("")
        private set
    var contatos by mutableStateOf<List<Contato>>(emptyList())
        private set
    var buscando by mutableStateOf(false)
        private set
    var destino by mutableStateOf<Contato?>(null)
        private set
    var valorTexto by mutableStateOf("")
    var descricao by mutableStateOf("")
    var enviando by mutableStateOf(false)
        private set
    var concluida by mutableStateOf<Transacao?>(null)
        private set
    var erro by mutableStateOf<String?>(null)
        private set

    /** Mesma chave em todas as tentativas deste pagamento: se a rede cair e o app reenviar, não cobra duas vezes. */
    private var chaveIdempotencia = UUID.randomUUID().toString()
    private var buscaJob: Job? = null

    val valor: BigDecimal?
        get() = valorTexto.replace(".", "").replace(',', '.').toBigDecimalOrNull()
            ?.takeIf { it.signum() > 0 && it.scale() <= 2 }

    fun aoMudarBusca(texto: String) {
        busca = texto
        buscaJob?.cancel()
        if (texto.trim().length < 3) {
            contatos = emptyList()
            return
        }
        buscaJob = viewModelScope.launch {
            delay(300)
            buscando = true
            runCatching { api.contatos(texto.trim()) }
                .onSuccess { contatos = it; erro = null }
                .onFailure { erro = it.mensagemAmigavel() }
            buscando = false
        }
    }

    fun escolher(contato: Contato?) {
        destino = contato
        erro = null
    }

    fun pagar() {
        val para = destino ?: return
        val quanto = valor ?: return
        if (enviando) return
        enviando = true
        erro = null
        viewModelScope.launch {
            runCatching {
                api.transferir(chaveIdempotencia, TransferenciaRequest(para.id, quanto, descricao.ifBlank { null }))
            }
                .onSuccess { concluida = it }
                .onFailure { erro = it.mensagemAmigavel() }
            enviando = false
        }
    }

    fun novoPagamento() {
        chaveIdempotencia = UUID.randomUUID().toString()
        destino = null
        concluida = null
        valorTexto = ""
        descricao = ""
        busca = ""
        contatos = emptyList()
    }
}

@Composable
fun PagarScreen(aoConcluir: () -> Unit, contentPadding: PaddingValues) {
    val vm = valtreaViewModel { PagarViewModel(it.api) }
    val modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
        .imePadding()
        .padding(16.dp)

    val concluida = vm.concluida
    val destino = vm.destino
    when {
        concluida != null -> Comprovante(concluida, modifier) {
            vm.novoPagamento()
            aoConcluir()
        }
        destino != null -> FormularioValor(vm, destino, modifier)
        else -> BuscaContato(vm, modifier)
    }
}

@Composable
private fun BuscaContato(vm: PagarViewModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Para quem você quer pagar?", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = vm.busca,
            onValueChange = vm::aoMudarBusca,
            label = { Text("Nome ou e-mail") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = { if (vm.buscando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (vm.busca.trim().length >= 3 && vm.contatos.isEmpty() && !vm.buscando) {
            Text("Ninguém encontrado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.contatos, key = { it.id }) { contato ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { vm.escolher(contato) }
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(contato.nome)
                    Column {
                        Text(contato.nome, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            contato.emailMascarado,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormularioValor(vm: PagarViewModel, destino: Contato, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(destino.nome)
            Column {
                Text("Pagando para", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(destino.nome, style = MaterialTheme.typography.titleMedium)
            }
        }
        OutlinedTextField(
            value = vm.valorTexto,
            onValueChange = { vm.valorTexto = it },
            label = { Text("Valor (R$)") },
            placeholder = { Text("0,00") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.descricao,
            onValueChange = { if (it.length <= 140) vm.descricao = it },
            label = { Text("Mensagem (opcional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = vm::pagar,
            enabled = vm.valor != null && !vm.enviando,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (vm.enviando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(vm.valor?.let { "Pagar ${it.emReais()}" } ?: "Pagar")
        }
        OutlinedButton(onClick = { vm.escolher(null) }, modifier = Modifier.fillMaxWidth()) {
            Text("Trocar destinatário")
        }
    }
}

@Composable
private fun Comprovante(t: Transacao, modifier: Modifier, aoFechar: () -> Unit) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Valtrea.VerdePrincipal,
            modifier = Modifier.size(72.dp),
        )
        Text("Pagamento enviado", style = MaterialTheme.typography.headlineSmall)
        Text(t.valor.emReais(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        t.contraparte?.let { Text("para ${it.nome}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(
            "Código ${t.id.take(8).uppercase()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = aoFechar, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Concluir") }
    }
}

@Composable
private fun Avatar(nome: String) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(Valtrea.VerdeEscuro),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            nome.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() },
            color = Valtrea.VerdePrincipal,
            fontWeight = FontWeight.Bold,
        )
    }
}

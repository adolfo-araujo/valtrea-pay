package br.com.valtrea.pay.ui.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.valtrea.pay.BuildConfig
import br.com.valtrea.pay.data.DepositoRequest
import br.com.valtrea.pay.data.Direcao
import br.com.valtrea.pay.data.Perfil
import br.com.valtrea.pay.data.TipoTransacao
import br.com.valtrea.pay.data.Transacao
import br.com.valtrea.pay.data.ValtreaApi
import br.com.valtrea.pay.data.mensagemAmigavel
import br.com.valtrea.pay.ui.componentes.dataCurta
import br.com.valtrea.pay.ui.componentes.emReais
import br.com.valtrea.pay.ui.theme.Valtrea
import br.com.valtrea.pay.ui.valtreaViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal

class InicioViewModel(private val api: ValtreaApi) : ViewModel() {

    var perfil by mutableStateOf<Perfil?>(null)
        private set
    var transacoes by mutableStateOf<List<Transacao>>(emptyList())
        private set
    var atualizando by mutableStateOf(false)
        private set
    var erro by mutableStateOf<String?>(null)
        private set

    fun atualizar() {
        if (atualizando) return
        atualizando = true
        viewModelScope.launch {
            runCatching {
                // coroutineScope faz a falha de uma das buscas chegar ao runCatching. Sem ele, o erro
                // subiria direto para o viewModelScope e fecharia o app quando a API estivesse fora do ar.
                coroutineScope {
                    val p = async { api.perfil() }
                    val t = async { api.extrato() }
                    p.await() to t.await().itens
                }
            }
                .onSuccess { (p, t) ->
                    perfil = p
                    transacoes = t
                    erro = null
                }
                .onFailure { erro = it.mensagemAmigavel() }
            atualizando = false
        }
    }

    fun depositarTeste(valor: BigDecimal) {
        viewModelScope.launch {
            runCatching { api.depositar(DepositoRequest(valor)) }
                .onSuccess { atualizar() }
                .onFailure { erro = it.mensagemAmigavel() }
        }
    }
}

@Composable
fun InicioScreen(
    aoPagar: () -> Unit,
    aoReceber: () -> Unit,
    aoConfirmarEmail: () -> Unit,
    contentPadding: PaddingValues,
) {
    val vm = valtreaViewModel { InicioViewModel(it.api) }
    var mostrarDeposito by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.atualizar() }

    PullToRefreshBox(
        isRefreshing = vm.atualizando,
        onRefresh = vm::atualizar,
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    "Olá, ${vm.perfil?.nome?.substringBefore(' ') ?: ""}",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (vm.perfil?.emailConfirmado == false) {
                item { AvisoConfirmarEmail(aoConfirmarEmail) }
            }
            item {
                CartaoSaldo(
                    saldo = vm.perfil?.saldo,
                    aoPagar = aoPagar,
                    aoReceber = aoReceber,
                    aoDepositar = { mostrarDeposito = true },
                )
            }
            vm.erro?.let { mensagem ->
                item { Text(mensagem, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Text(
                    "Atividades",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (vm.transacoes.isEmpty() && !vm.atualizando) {
                item {
                    Text(
                        "Nenhuma movimentação ainda.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(vm.transacoes, key = { it.id }) { ItemTransacao(it) }
        }
    }

    if (mostrarDeposito) {
        DialogoDeposito(
            aoConfirmar = {
                mostrarDeposito = false
                vm.depositarTeste(it)
            },
            aoFechar = { mostrarDeposito = false },
        )
    }
}

@Composable
private fun AvisoConfirmarEmail(aoConfirmar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = aoConfirmar)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.MarkEmailUnread, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Column(Modifier.weight(1f)) {
            Text("Confirme seu e-mail", fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text("Necessário para enviar pagamentos.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text("Confirmar", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CartaoSaldo(saldo: BigDecimal?, aoPagar: () -> Unit, aoReceber: () -> Unit, aoDepositar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Valtrea.Gradiente)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text("Saldo disponível", color = Valtrea.Branco.copy(alpha = 0.85f))
            Text(
                saldo?.emReais() ?: "—",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Valtrea.Branco,
            )
        }
        // Botões brancos com texto verde-escuro, como a versão "sobre fundo da marca" do guia.
        val coresBotao = ButtonDefaults.filledTonalButtonColors(
            containerColor = Valtrea.Branco,
            contentColor = Valtrea.VerdeEscuro,
        )
        // FlowRow quebra para a linha de baixo em telas estreitas.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = aoPagar, colors = coresBotao) {
                Icon(Icons.Default.Send, contentDescription = null, Modifier.size(18.dp))
                Text("  Pagar")
            }
            FilledTonalButton(onClick = aoReceber, colors = coresBotao) {
                Icon(Icons.Default.QrCode, contentDescription = null, Modifier.size(18.dp))
                Text("  Receber")
            }
            // Só nas versões de teste (debug e prévia), ligadas a uma API com VALTREA_SANDBOX=true.
            if (BuildConfig.DEPOSITO_TESTE) {
                FilledTonalButton(onClick = aoDepositar, colors = coresBotao) {
                    Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                    Text("  Depositar")
                }
            }
        }
    }
}

@Composable
private fun ItemTransacao(t: Transacao) {
    val entrada = t.direcao == Direcao.ENTRADA
    val titulo = when {
        t.tipo == TipoTransacao.DEPOSITO -> "Depósito"
        entrada -> "Recebido de ${t.contraparte?.nome ?: "—"}"
        else -> "Pago para ${t.contraparte?.nome ?: "—"}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (entrada) Valtrea.VerdePrincipal.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (entrada) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                contentDescription = if (entrada) "Entrada" else "Saída",
                tint = if (entrada) Valtrea.VerdePrincipal else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(t.descricao, t.criadoEm.dataCurta()).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            (if (entrada) "+ " else "− ") + t.valor.emReais(),
            fontWeight = FontWeight.SemiBold,
            color = if (entrada) Valtrea.VerdePrincipal else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DialogoDeposito(aoConfirmar: (BigDecimal) -> Unit, aoFechar: () -> Unit) {
    var texto by remember { mutableStateOf("100,00") }
    val valor = texto.replace(".", "").replace(',', '.').toBigDecimalOrNull()
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Depósito de teste") },
        text = {
            OutlinedTextField(value = texto, onValueChange = { texto = it }, label = { Text("Valor (R$)") })
        },
        confirmButton = {
            TextButton(onClick = { valor?.let(aoConfirmar) }, enabled = valor != null) { Text("Depositar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

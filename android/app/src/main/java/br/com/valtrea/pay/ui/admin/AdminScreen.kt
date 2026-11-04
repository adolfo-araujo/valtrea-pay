package br.com.valtrea.pay.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.valtrea.pay.data.MetricasAdmin
import br.com.valtrea.pay.data.SemanaAdmin
import br.com.valtrea.pay.data.ValtreaApi
import br.com.valtrea.pay.data.mensagemAmigavel
import br.com.valtrea.pay.ui.componentes.BotaoVoltar
import br.com.valtrea.pay.ui.componentes.emReais
import br.com.valtrea.pay.ui.valtreaViewModel
import kotlinx.coroutines.launch

class AdminViewModel(private val api: ValtreaApi) : ViewModel() {
    var metricas by mutableStateOf<MetricasAdmin?>(null)
        private set
    var carregando by mutableStateOf(false)
        private set
    var erro by mutableStateOf<String?>(null)
        private set

    fun carregar() {
        if (carregando) return
        carregando = true
        erro = null
        viewModelScope.launch {
            runCatching { api.metricasAdmin() }
                .onSuccess { metricas = it }
                .onFailure { erro = it.mensagemAmigavel() }
            carregando = false
        }
    }
}

/** Painel de administração: números de uso agregados. Só aparece para contas de administração. */
@Composable
fun AdminScreen(aoVoltar: () -> Unit, contentPadding: PaddingValues) {
    val vm = valtreaViewModel { AdminViewModel(it.api) }
    LaunchedEffect(Unit) { vm.carregar() }
    PainelAdmin(
        metricas = vm.metricas,
        carregando = vm.carregando,
        erro = vm.erro,
        aoAtualizar = vm::carregar,
        aoVoltar = aoVoltar,
        contentPadding = contentPadding,
    )
}

/** A tela sem a parte de rede, para os testes conseguirem mostrar números conhecidos. */
@Composable
fun PainelAdmin(
    metricas: MetricasAdmin?,
    carregando: Boolean,
    erro: String?,
    aoAtualizar: () -> Unit,
    aoVoltar: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BotaoVoltar(aoVoltar, Modifier.offset(x = (-12).dp))
        Text(
            "Administração",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Só números somados de todas as contas. Nenhum nome, e-mail ou transação de alguém aparece aqui. " +
                "Os valores são da carteira de teste (saldo fictício).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (metricas == null && carregando) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        metricas?.let { m ->
            Secao("Contas")
            Numeros(
                "Ativas" to m.contas.ativas.toString(),
                "Com e-mail confirmado" to m.contas.confirmadas.toString(),
                "Novas em 7 dias" to m.contas.novas7Dias.toString(),
                "Novas em 30 dias" to m.contas.novas30Dias.toString(),
            )
            Secao("Uso")
            Numeros(
                "Conectadas em algum aparelho" to m.atividade.comSessaoAtiva.toString(),
                "Pagaram alguém em 7 dias" to m.atividade.pagantes7Dias.toString(),
                "Transações em 24 horas" to m.atividade.transacoes24Horas.toString(),
                "Transações em 7 dias" to m.atividade.transacoes7Dias.toString(),
                "Transações em 30 dias" to m.atividade.transacoes30Dias.toString(),
                "Volume pago em 30 dias (fictício)" to m.atividade.volume30Dias.emReais(),
            )
            Secao("Integração com o Valtrea Finance")
            Numeros(
                "Contas conectadas" to m.finance.contasConectadas.toString(),
                "Envios na fila" to m.finance.enviosPendentes.toString(),
                "Envios com falha (tentando de novo)" to m.finance.enviosComFalha.toString(),
            )
            Secao("Cadastros por semana")
            GraficoSemanas(m.cadastrosPorSemana)
            Text(
                "Atualizado em ${m.geradoEm.take(16).replace('T', ' ')} (UTC). " +
                    "Excluídas aguardando limpeza: ${m.contas.excluidasAguardando}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(
            onClick = aoAtualizar,
            enabled = !carregando,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text(if (carregando) "Atualizando…" else "Atualizar") }
    }
}

@Composable
private fun Secao(titulo: String) {
    Text(
        titulo,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun Numeros(vararg itens: Pair<String, String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        itens.forEach { (rotulo, valor) ->
            // O leitor de tela lê a linha inteira de uma vez ("Ativas: 12").
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clearAndSetSemantics { contentDescription = "$rotulo: $valor" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    rotulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(valor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Barras simples; os mesmos números também são lidos pelo leitor de tela. */
@Composable
private fun GraficoSemanas(semanas: List<SemanaAdmin>) {
    val maximo = (semanas.maxOfOrNull { it.cadastros } ?: 0L).coerceAtLeast(1L)
    val descricao = semanas.joinToString("; ") { "semana de ${dataCurta(it.semana)}: ${it.cadastros}" }
    Row(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
            .clearAndSetSemantics { contentDescription = "Cadastros por semana: $descricao" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        semanas.forEach { semana ->
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(semana.cadastros.toString(), style = MaterialTheme.typography.labelSmall)
                val fracao = semana.cadastros.toFloat() / maximo
                Box(
                    Modifier
                        .width(18.dp)
                        .fillMaxHeight(0.7f * fracao + 0.02f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(dataCurta(semana.semana), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** "2026-11-02" → "02/11". */
private fun dataCurta(iso: String): String =
    if (iso.length >= 10) "${iso.substring(8, 10)}/${iso.substring(5, 7)}" else iso

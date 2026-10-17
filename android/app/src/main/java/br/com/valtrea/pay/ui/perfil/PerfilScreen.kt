package br.com.valtrea.pay.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.valtrea.pay.BuildConfig
import br.com.valtrea.pay.data.Perfil
import br.com.valtrea.pay.data.Sessao
import br.com.valtrea.pay.data.ValtreaApi
import br.com.valtrea.pay.ui.componentes.LogoValtrea
import br.com.valtrea.pay.ui.valtreaViewModel
import kotlinx.coroutines.launch

class PerfilViewModel(private val api: ValtreaApi, private val sessao: Sessao) : ViewModel() {
    var perfil by mutableStateOf<Perfil?>(null)
        private set

    fun carregar() {
        viewModelScope.launch { runCatching { api.perfil() }.onSuccess { perfil = it } }
    }

    fun sair() = sessao.encerrar()
}

@Composable
fun PerfilScreen(contentPadding: PaddingValues) {
    val vm = valtreaViewModel { PerfilViewModel(it.api, it.sessao) }
    LaunchedEffect(Unit) { vm.carregar() }

    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Perfil", style = MaterialTheme.typography.titleLarge)
        vm.perfil?.let { p ->
            ListItem(headlineContent = { Text(p.nome) }, supportingContent = { Text("Nome") })
            HorizontalDivider()
            ListItem(headlineContent = { Text(p.email) }, supportingContent = { Text("E-mail") })
            HorizontalDivider()
            ListItem(headlineContent = { Text(p.telefone) }, supportingContent = { Text("Celular") })
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = vm::sair, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Sair") }
        Spacer(Modifier.height(8.dp))
        LogoValtrea(tamanhoIcone = 16.dp)
        Text(
            "Valtrea Pay ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

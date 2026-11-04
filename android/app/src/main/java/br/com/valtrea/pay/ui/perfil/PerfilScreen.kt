package br.com.valtrea.pay.ui.perfil

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import br.com.valtrea.pay.BuildConfig
import br.com.valtrea.pay.data.AtualizarPerfilRequest
import br.com.valtrea.pay.data.ExcluirContaRequest
import br.com.valtrea.pay.ui.componentes.Links
import br.com.valtrea.pay.data.Perfil
import br.com.valtrea.pay.data.RenovarRequest
import br.com.valtrea.pay.data.Sessao
import br.com.valtrea.pay.data.ValtreaApi
import br.com.valtrea.pay.data.mensagemAmigavel
import br.com.valtrea.pay.ui.componentes.LogoValtrea
import br.com.valtrea.pay.ui.valtreaViewModel
import kotlinx.coroutines.launch

class PerfilViewModel(private val api: ValtreaApi, private val sessao: Sessao) : ViewModel() {
    var perfil by mutableStateOf<Perfil?>(null)
        private set

    var editando by mutableStateOf(false)
        private set
    var nome by mutableStateOf("")
    var telefone by mutableStateOf("")
    var salvando by mutableStateOf(false)
        private set
    var erro by mutableStateOf<String?>(null)
        private set

    fun carregar() {
        viewModelScope.launch { runCatching { api.perfil() }.onSuccess { perfil = it } }
    }

    fun editar() {
        val atual = perfil ?: return
        nome = atual.nome
        telefone = atual.telefone
        erro = null
        editando = true
    }

    fun cancelarEdicao() {
        editando = false
    }

    fun salvar() {
        if (salvando) return
        salvando = true
        erro = null
        viewModelScope.launch {
            runCatching { api.atualizarPerfil(AtualizarPerfilRequest(nome.trim(), telefone.trim())) }
                .onSuccess {
                    perfil = it
                    editando = false
                }
                .onFailure { erro = it.mensagemAmigavel() }
            salvando = false
        }
    }

    var baixandoDados by mutableStateOf(false)
        private set
    var avisoDados by mutableStateOf<String?>(null)
        private set

    /** Salva o arquivo de "Baixar meus dados" onde a pessoa escolheu. */
    fun baixarDados(resolver: ContentResolver, destino: Uri) {
        if (baixandoDados) return
        baixandoDados = true
        avisoDados = null
        viewModelScope.launch {
            runCatching {
                val corpo = api.meusDados()
                withContext(Dispatchers.IO) {
                    corpo.use { resposta ->
                        val saida = resolver.openOutputStream(destino) ?: error("Não foi possível criar o arquivo.")
                        saida.use { resposta.byteStream().copyTo(it) }
                    }
                }
            }
                .onSuccess { avisoDados = "Pronto: seus dados foram salvos no arquivo escolhido." }
                .onFailure { avisoDados = it.mensagemAmigavel() }
            baixandoDados = false
        }
    }

    var excluindo by mutableStateOf(false)
        private set
    var senhaExclusao by mutableStateOf("")
    var erroExclusao by mutableStateOf<String?>(null)
        private set
    var processandoExclusao by mutableStateOf(false)
        private set

    fun pedirExclusao() {
        senhaExclusao = ""
        erroExclusao = null
        excluindo = true
    }

    fun cancelarExclusao() {
        excluindo = false
    }

    /** Exclui a conta na API e sai do app. Só sai se a API confirmar. */
    fun confirmarExclusao() {
        if (processandoExclusao) return
        processandoExclusao = true
        erroExclusao = null
        viewModelScope.launch {
            runCatching { api.excluirConta(ExcluirContaRequest(senhaExclusao)) }
                .onSuccess { sessao.encerrar() }
                .onFailure { erroExclusao = it.mensagemAmigavel() }
            processandoExclusao = false
        }
    }

    /** Avisa a API para encerrar a sessão deste aparelho; sai do app mesmo se estiver sem internet. */
    fun sair() {
        val token = sessao.tokenSessao
        viewModelScope.launch {
            if (token != null) runCatching { api.sair(RenovarRequest(token)) }
            sessao.encerrar()
        }
    }
}

@Composable
fun PerfilScreen(contentPadding: PaddingValues) {
    val vm = valtreaViewModel { PerfilViewModel(it.api, it.sessao) }
    LaunchedEffect(Unit) { vm.carregar() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Perfil", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (vm.perfil != null) {
                TextButton(onClick = vm::editar) {
                    Icon(Icons.Default.Edit, contentDescription = null, Modifier.size(18.dp))
                    Text("  Editar")
                }
            }
        }
        vm.perfil?.let { p ->
            ListItem(headlineContent = { Text(p.nome) }, supportingContent = { Text("Nome") })
            HorizontalDivider()
            ListItem(headlineContent = { Text(p.email) }, supportingContent = { Text("E-mail") })
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(p.telefone.ifBlank { "Não informado" }) },
                supportingContent = { Text("Celular (opcional)") },
            )
        }
        if (vm.editando) DialogoEditarPerfil(vm)
        Spacer(Modifier.height(8.dp))
        FinanceSecao()
        Spacer(Modifier.height(16.dp))
        val contexto = LocalContext.current
        val salvarDados = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
            uri -> if (uri != null) vm.baixarDados(contexto.contentResolver, uri)
        }
        Text("Seus dados", style = MaterialTheme.typography.titleMedium)
        Text(
            "Baixe uma cópia do seu cadastro, do saldo e de todas as transações, em um arquivo que outros serviços " +
                "conseguem ler (JSON).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { salvarDados.launch("valtrea-pay-meus-dados.json") },
            enabled = vm.perfil != null && !vm.baixandoDados,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (vm.baixandoDados) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Baixar meus dados")
        }
        vm.avisoDados?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = vm::sair, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Sair") }
        Column(Modifier.fillMaxWidth()) {
            TextButton(onClick = { Links.abrir(contexto, Links.termos) }) { Text("Termos de uso") }
            TextButton(onClick = { Links.abrir(contexto, Links.privacidade) }) { Text("Política de privacidade") }
            TextButton(onClick = { Links.abrir(contexto, Links.acessibilidade) }) { Text("Acessibilidade") }
            TextButton(onClick = vm::pedirExclusao, enabled = vm.perfil != null) {
                Text("Excluir conta", color = MaterialTheme.colorScheme.error)
            }
        }
        if (vm.excluindo) DialogoExcluirConta(vm)
        Spacer(Modifier.height(8.dp))
        LogoValtrea(tamanhoIcone = 16.dp)
        Text(
            "Valtrea Pay ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DialogoExcluirConta(vm: PerfilViewModel) {
    AlertDialog(
        onDismissRequest = { if (!vm.processandoExclusao) vm.cancelarExclusao() },
        title = { Text("Excluir conta?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Seu nome, e-mail, CPF, celular e senha serão apagados, e todos os aparelhos serão " +
                        "desconectados. Isso não pode ser desfeito.\n\nOs pagamentos feitos com outras pessoas " +
                        "continuam no extrato delas, sem o seu nome. Se quiser uma cópia dos seus dados, use " +
                        "\"Baixar meus dados\" antes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = vm.senhaExclusao,
                    onValueChange = { vm.senhaExclusao = it },
                    label = { Text("Digite sua senha para confirmar") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                vm.erroExclusao?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = vm::confirmarExclusao,
                enabled = vm.senhaExclusao.isNotEmpty() && !vm.processandoExclusao,
            ) {
                if (vm.processandoExclusao) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Excluir definitivamente", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = vm::cancelarExclusao, enabled = !vm.processandoExclusao) { Text("Cancelar") }
        },
    )
}

/** E-mail e CPF não aparecem aqui: o e-mail precisaria de nova confirmação e o CPF identifica a pessoa. */
@Composable
private fun DialogoEditarPerfil(vm: PerfilViewModel) {
    AlertDialog(
        onDismissRequest = { if (!vm.salvando) vm.cancelarEdicao() },
        title = { Text("Editar perfil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = vm.nome,
                    onValueChange = { if (it.length <= 120) vm.nome = it },
                    label = { Text("Nome completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = vm.telefone,
                    onValueChange = { if (it.length <= 20) vm.telefone = it },
                    label = { Text("Celular (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::salvar, enabled = !vm.salvando && vm.nome.isNotBlank()) {
                if (vm.salvando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = vm::cancelarEdicao, enabled = !vm.salvando) { Text("Cancelar") }
        },
    )
}

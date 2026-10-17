package br.com.valtrea.pay.ui.entrar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.valtrea.pay.data.CadastroRequest
import br.com.valtrea.pay.data.LoginRequest
import br.com.valtrea.pay.data.Sessao
import br.com.valtrea.pay.data.ValtreaApi
import br.com.valtrea.pay.data.mensagemAmigavel
import br.com.valtrea.pay.ui.componentes.LogoValtrea
import br.com.valtrea.pay.ui.valtreaViewModel
import kotlinx.coroutines.launch

class EntrarViewModel(private val api: ValtreaApi, private val sessao: Sessao) : ViewModel() {

    var criandoConta by mutableStateOf(false)
    var nome by mutableStateOf("")
    var email by mutableStateOf("")
    var cpf by mutableStateOf("")
    var telefone by mutableStateOf("")
    var senha by mutableStateOf("")
    var enviando by mutableStateOf(false)
        private set
    var erro by mutableStateOf<String?>(null)
        private set

    fun enviar() {
        if (enviando) return
        enviando = true
        erro = null
        viewModelScope.launch {
            runCatching {
                if (criandoConta) {
                    api.cadastrar(CadastroRequest(nome, email, cpf, telefone, senha))
                } else {
                    api.entrar(LoginRequest(email, senha))
                }
            }
                .onSuccess { sessao.salvar(it) } // A navegação reage à sessão.
                .onFailure { erro = it.mensagemAmigavel() }
            enviando = false
        }
    }
}

@Composable
fun EntrarScreen() {
    val vm = valtreaViewModel { EntrarViewModel(it.api, it.sessao) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        LogoValtrea()
        Text(
            if (vm.criandoConta) "Crie sua carteira Valtrea" else "Pague e receba em segundos",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (vm.criandoConta) {
            Campo("Nome completo", vm.nome, { vm.nome = it })
            Campo("CPF", vm.cpf, { vm.cpf = it }, KeyboardType.Number)
            Campo("Celular", vm.telefone, { vm.telefone = it }, KeyboardType.Phone)
        }
        Campo("E-mail", vm.email, { vm.email = it }, KeyboardType.Email)
        Campo("Senha", vm.senha, { vm.senha = it }, KeyboardType.Password, senha = true)

        vm.erro?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = vm::enviar,
            enabled = !vm.enviando,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (vm.enviando) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (vm.criandoConta) "Criar conta" else "Entrar")
            }
        }
        TextButton(onClick = { vm.criandoConta = !vm.criandoConta }) {
            Text(if (vm.criandoConta) "Já tenho conta" else "Ainda não tenho conta")
        }
    }
}

@Composable
private fun Campo(
    rotulo: String,
    valor: String,
    aoMudar: (String) -> Unit,
    teclado: KeyboardType = KeyboardType.Text,
    senha: Boolean = false,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        visualTransformation = if (senha) PasswordVisualTransformation() else
            androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

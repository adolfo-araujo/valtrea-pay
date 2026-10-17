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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.valtrea.pay.data.CadastroRequest
import br.com.valtrea.pay.data.EsqueciSenhaRequest
import br.com.valtrea.pay.data.LoginRequest
import br.com.valtrea.pay.data.RedefinirSenhaRequest
import br.com.valtrea.pay.data.Sessao
import br.com.valtrea.pay.data.ValtreaApi
import br.com.valtrea.pay.data.mensagemAmigavel
import br.com.valtrea.pay.ui.componentes.LogoValtrea
import br.com.valtrea.pay.ui.valtreaViewModel
import kotlinx.coroutines.launch

enum class Modo { ENTRAR, CRIAR, ESQUECI, REDEFINIR }

class EntrarViewModel(private val api: ValtreaApi, private val sessao: Sessao) : ViewModel() {

    var modo by mutableStateOf(Modo.ENTRAR)
        private set
    var nome by mutableStateOf("")
    var email by mutableStateOf("")
    var cpf by mutableStateOf("")
    var telefone by mutableStateOf("")
    var senha by mutableStateOf("")
    var codigo by mutableStateOf("")
    var enviando by mutableStateOf(false)
        private set
    var erro by mutableStateOf<String?>(null)
        private set
    var aviso by mutableStateOf<String?>(null)
        private set

    fun irPara(novo: Modo) {
        modo = novo
        erro = null
        aviso = null
        senha = ""
        codigo = ""
    }

    fun enviar() {
        if (enviando) return
        enviando = true
        erro = null
        aviso = null
        viewModelScope.launch {
            runCatching {
                when (modo) {
                    Modo.ENTRAR -> sessao.salvar(api.entrar(LoginRequest(email, senha)))
                    Modo.CRIAR -> sessao.salvar(api.cadastrar(CadastroRequest(nome, email, cpf, telefone, senha)))
                    Modo.ESQUECI -> {
                        api.esqueciSenha(EsqueciSenhaRequest(email))
                        irPara(Modo.REDEFINIR)
                        aviso = "Se houver uma conta com este e-mail, enviamos um código de 6 números."
                    }
                    Modo.REDEFINIR -> {
                        api.redefinirSenha(RedefinirSenhaRequest(email, codigo, senha))
                        irPara(Modo.ENTRAR)
                        aviso = "Senha alterada. Entre com a nova senha."
                    }
                }
            }.onFailure { erro = it.mensagemAmigavel() }
            enviando = false
        }
    }
}

@Composable
fun EntrarScreen() {
    val vm = valtreaViewModel { EntrarViewModel(it.api, it.sessao) }
    val modo = vm.modo

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
            when (modo) {
                Modo.ENTRAR -> "Pague e receba em segundos"
                Modo.CRIAR -> "Crie sua carteira Valtrea"
                Modo.ESQUECI -> "Vamos enviar um código para o seu e-mail"
                Modo.REDEFINIR -> "Digite o código e escolha uma senha nova"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))

        if (modo == Modo.CRIAR) {
            Campo("Nome completo", vm.nome, { vm.nome = it })
            Campo("CPF", vm.cpf, { vm.cpf = it }, KeyboardType.Number)
            Campo("Celular", vm.telefone, { vm.telefone = it }, KeyboardType.Phone)
        }
        Campo("E-mail", vm.email, { vm.email = it }, KeyboardType.Email, habilitado = modo != Modo.REDEFINIR)
        if (modo == Modo.REDEFINIR) {
            Campo("Código de 6 números", vm.codigo, { if (it.length <= 6) vm.codigo = it.filter(Char::isDigit) },
                KeyboardType.NumberPassword)
        }
        if (modo != Modo.ESQUECI) {
            Campo(if (modo == Modo.REDEFINIR) "Nova senha" else "Senha", vm.senha, { vm.senha = it },
                KeyboardType.Password, senha = true)
        }

        vm.aviso?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center)
        }
        vm.erro?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center)
        }

        Button(
            onClick = vm::enviar,
            enabled = !vm.enviando,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (vm.enviando) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    when (modo) {
                        Modo.ENTRAR -> "Entrar"
                        Modo.CRIAR -> "Criar conta"
                        Modo.ESQUECI -> "Enviar código"
                        Modo.REDEFINIR -> "Salvar nova senha"
                    },
                )
            }
        }

        when (modo) {
            Modo.ENTRAR -> {
                TextButton(onClick = { vm.irPara(Modo.ESQUECI) }) { Text("Esqueci minha senha") }
                TextButton(onClick = { vm.irPara(Modo.CRIAR) }) { Text("Ainda não tenho conta") }
            }
            Modo.CRIAR -> TextButton(onClick = { vm.irPara(Modo.ENTRAR) }) { Text("Já tenho conta") }
            Modo.ESQUECI, Modo.REDEFINIR -> TextButton(onClick = { vm.irPara(Modo.ENTRAR) }) { Text("Voltar") }
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
    habilitado: Boolean = true,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        singleLine = true,
        enabled = habilitado,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        visualTransformation = if (senha) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

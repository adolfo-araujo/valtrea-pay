package br.com.valtrea.pay.ui.entrar

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.valtrea.pay.ui.theme.ValtreaTheme
import br.com.valtrea.pay.ui.verificarAcessibilidade
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tela de entrada (login, cadastro e "esqueci a senha"), nos temas claro e escuro. */
@RunWith(AndroidJUnit4::class)
class EntrarScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun abrir(escuro: Boolean = false) {
        compose.setContent { ValtreaTheme(escuro = escuro) { EntrarScreen() } }
    }

    @Test
    fun loginTemCamposComRotuloESenhaEscondida() {
        abrir()
        compose.onNodeWithText("E-mail").assertIsDisplayed()
        compose.onNodeWithText("Senha")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("Entrar").assertIsDisplayed()
        compose.verificarAcessibilidade()
    }

    @Test
    fun cadastroPedeCpfCelularOpcionalEAvisaQueOSaldoEFicticio() {
        abrir()
        compose.onNodeWithText("Ainda não tenho conta").performClick()
        compose.onNodeWithText("Nome completo").assertIsDisplayed()
        compose.onNodeWithText("CPF").assertIsDisplayed()
        compose.onNodeWithText("Celular (opcional)").assertIsDisplayed()
        compose.onNodeWithText("saldo é fictício", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ler os Termos de uso").assertIsDisplayed()
        compose.onNodeWithText("Ler a Política de privacidade").assertIsDisplayed()
        compose.onNodeWithText("Criar conta").assertIsDisplayed()
        compose.verificarAcessibilidade()
    }

    @Test
    fun esqueciASenhaPedeSoOEmail() {
        abrir()
        compose.onNodeWithText("Esqueci minha senha").performClick()
        compose.onNodeWithText("Enviar código").assertIsDisplayed()
        compose.onNodeWithText("Senha").assertDoesNotExist()
        compose.onNodeWithText("Voltar").performClick()
        compose.onNodeWithText("Entrar").assertIsDisplayed()
    }

    @Test
    fun temaEscuroTambemPassaNasVerificacoes() {
        abrir(escuro = true)
        compose.verificarAcessibilidade()
        compose.onNodeWithText("Ainda não tenho conta").performClick()
        compose.verificarAcessibilidade()
    }
}

package br.com.valtrea.pay.ui.inicio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.valtrea.pay.ui.theme.ValtreaTheme
import br.com.valtrea.pay.ui.verificarAcessibilidade
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AvisoNovosTermosTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun avisoTemLinksEAceite() {
        var aceites = 0
        compose.setContent { ValtreaTheme(escuro = false) { AvisoNovosTermos(aoAceitar = { aceites++ }) } }
        compose.onNodeWithText("Atualizamos os Termos de uso e a Política de privacidade").assertIsDisplayed()
        compose.onNodeWithText("Termos").assertIsDisplayed()
        compose.onNodeWithText("Privacidade").assertIsDisplayed()
        compose.onNodeWithText("Li e concordo").performClick()
        assertEquals(1, aceites)
        compose.verificarAcessibilidade()
    }
}

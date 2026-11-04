package br.com.valtrea.pay.ui.admin

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.valtrea.pay.data.AtividadeAdmin
import br.com.valtrea.pay.data.ContasAdmin
import br.com.valtrea.pay.data.FinanceAdmin
import br.com.valtrea.pay.data.MetricasAdmin
import br.com.valtrea.pay.data.SemanaAdmin
import br.com.valtrea.pay.ui.theme.ValtreaTheme
import br.com.valtrea.pay.ui.verificarAcessibilidade
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PainelAdminTest {

    @get:Rule
    val compose = createComposeRule()

    private val metricas = MetricasAdmin(
        geradoEm = "2026-11-04T12:30:00Z",
        contas = ContasAdmin(ativas = 12, confirmadas = 9, novas7Dias = 3, novas30Dias = 7, excluidasAguardando = 1),
        atividade = AtividadeAdmin(
            comSessaoAtiva = 8, pagantes7Dias = 4, transacoes24Horas = 2, transacoes7Dias = 15,
            transacoes30Dias = 40, volume30Dias = BigDecimal("1234.50"),
        ),
        finance = FinanceAdmin(contasConectadas = 2, enviosPendentes = 0, enviosComFalha = 0),
        cadastrosPorSemana = listOf(
            "2026-09-14", "2026-09-21", "2026-09-28", "2026-10-05",
            "2026-10-12", "2026-10-19", "2026-10-26", "2026-11-02",
        ).mapIndexed { i, semana -> SemanaAdmin(semana, i.toLong()) },
    )

    @Test
    fun mostraOsNumerosELeCadaLinhaInteira() {
        var atualizou = 0
        var voltou = 0
        compose.setContent {
            ValtreaTheme(escuro = false) {
                PainelAdmin(metricas, carregando = false, erro = null, aoAtualizar = { atualizou++ },
                    aoVoltar = { voltou++ })
            }
        }
        compose.onNodeWithText("Administração").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ativas: 12").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pagaram alguém em 7 dias: 4").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volume pago em 30 dias (fictício)", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Cadastros por semana", substring = true).assertIsDisplayed()

        val titulos = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .fetchSemanticsNodes()
        assertEquals("título da tela + 4 seções", 5, titulos.size)

        compose.onNodeWithText("Atualizar").performClick()
        compose.onNodeWithContentDescription("Voltar").performClick()
        assertEquals(1, atualizou)
        assertEquals(1, voltou)
        compose.verificarAcessibilidade()
    }

    @Test
    fun mostraOErroSemNumeros() {
        compose.setContent {
            ValtreaTheme(escuro = true) {
                PainelAdmin(null, carregando = false, erro = "Acesso restrito à administração.", aoAtualizar = {},
                    aoVoltar = {})
            }
        }
        compose.onNodeWithText("Acesso restrito à administração.").assertIsDisplayed()
        compose.onAllNodesWithText("Ativas", substring = true).assertCountEquals(0)
        compose.verificarAcessibilidade()
    }
}

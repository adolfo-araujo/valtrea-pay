package br.com.valtrea.pay.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contraste mínimo de 4,5:1 entre texto e fundo nos dois temas (WCAG 2.2, critério 1.4.3, nível AA). */
class ContrasteTest {

    private fun contraste(a: Color, b: Color): Double {
        val (claro, escuro) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (claro + 0.05) / (escuro + 0.05)
    }

    private fun pares(c: ColorScheme) = mapOf(
        "texto no fundo" to (c.onBackground to c.background),
        "texto na superfície" to (c.onSurface to c.surface),
        "texto secundário no fundo" to (c.onSurfaceVariant to c.background),
        "texto secundário em cartões" to (c.onSurfaceVariant to c.surfaceContainer),
        "texto secundário em cartões destacados" to (c.onSurfaceVariant to c.surfaceContainerHigh),
        "texto secundário em surfaceVariant" to (c.onSurfaceVariant to c.surfaceVariant),
        "texto de botão" to (c.onPrimary to c.primary),
        "links e botões de texto no fundo" to (c.primary to c.background),
        "avisos" to (c.onSecondaryContainer to c.secondaryContainer),
        "destaques" to (c.onPrimaryContainer to c.primaryContainer),
        "mensagens de erro" to (c.error to c.background),
    )

    private fun verificar(nome: String, esquema: ColorScheme) {
        val falhas = pares(esquema).mapNotNull { (uso, cores) ->
            val r = contraste(cores.first, cores.second)
            if (r < 4.5) "$uso: %.2f:1".format(r) else null
        }
        assertTrue("Contraste abaixo de 4,5:1 no tema $nome: $falhas", falhas.isEmpty())
    }

    @Test
    fun temaClaro() = verificar("claro", Claro)

    @Test
    fun temaEscuro() = verificar("escuro", Escuro)

    @Test
    fun textoBrancoNoCartaoDeSaldo() {
        listOf(Valtrea.VerdeAcessivel, Color(0xFF2E6619)).forEach {
            assertTrue(contraste(Valtrea.Branco, it) >= 4.5)
        }
    }
}

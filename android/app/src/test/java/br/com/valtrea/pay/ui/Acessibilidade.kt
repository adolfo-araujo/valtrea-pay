package br.com.valtrea.pay.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTouchHeightIsAtLeast
import androidx.compose.ui.test.assertTouchWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp

/**
 * Verificações automáticas de acessibilidade (WCAG 2.2 AA, as que dá para conferir sem uma pessoa):
 * - todo elemento tocável tem nome para o leitor de tela (texto ou descrição) — WCAG 4.1.2;
 * - todo campo de texto tem rótulo — WCAG 1.3.1 e 3.3.2;
 * - toda área de toque tem pelo menos 48 x 48 dp (recomendação do Android; a WCAG 2.5.8 pede 24) — WCAG 2.5.8.
 * O contraste das cores do tema é conferido em ContrasteTest.
 */
fun ComposeContentTestRule.verificarAcessibilidade() {
    val problemas = mutableListOf<String>()
    percorrer(onRoot().fetchSemanticsNode()) { no ->
        val descricao = nomeAcessivel(no)
        val clicavel = no.config.getOrNull(SemanticsActions.OnClick) != null
        val campo = no.config.getOrNull(SemanticsActions.SetText) != null
        if (clicavel && descricao.isBlank()) problemas += "elemento tocável sem nome (nó ${no.id})"
        if (campo && no.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty() && descricao.isBlank()) {
            problemas += "campo de texto sem rótulo (nó ${no.id})"
        }
    }
    check(problemas.isEmpty()) { "Problemas de acessibilidade:\n" + problemas.joinToString("\n") }

    val tocaveis = onAllNodes(hasClickAction()).fetchSemanticsNodes()
    tocaveis.indices.forEach { i ->
        val no: SemanticsNodeInteraction = onAllNodes(hasClickAction())[i]
        val nome = nomeAcessivel(tocaveis[i])
        runCatching { no.assertTouchHeightIsAtLeast(48.dp).assertTouchWidthIsAtLeast(48.dp) }
            .onFailure { throw AssertionError("Área de toque menor que 48 dp em \"$nome\": ${it.message}", it) }
    }
}

private fun nomeAcessivel(no: SemanticsNode): String {
    val textos = no.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    val descricoes = no.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    return (textos + descricoes).joinToString(" ").trim()
}

private fun percorrer(no: SemanticsNode, visitar: (SemanticsNode) -> Unit) {
    visitar(no)
    no.children.forEach { percorrer(it, visitar) }
}

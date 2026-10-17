package br.com.valtrea.pay.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Paleta oficial do guia de marca Valtrea.
object Valtrea {
    val VerdePrincipal = Color(0xFF22C55E)
    val VerdeSecundario = Color(0xFF16A34A)
    val VerdeEscuro = Color(0xFF0F3D2E)
    val Grafite = Color(0xFF111827)
    val Cinza = Color(0xFF6B7280)
    val CinzaClaro = Color(0xFFE5E7EB)
    val Branco = Color(0xFFFFFFFF)

    val Gradiente = Brush.linearGradient(listOf(VerdePrincipal, VerdeSecundario))
}

private val Escuro = darkColorScheme(
    primary = Valtrea.VerdePrincipal,
    onPrimary = Valtrea.Grafite,
    primaryContainer = Valtrea.VerdeEscuro,
    onPrimaryContainer = Valtrea.Branco,
    secondary = Valtrea.VerdeSecundario,
    // Usado no destaque da aba ativa e em botões "tonais". Sem isso o Material usa lilás.
    secondaryContainer = Valtrea.VerdeEscuro,
    onSecondaryContainer = Valtrea.VerdePrincipal,
    background = Color(0xFF0B1712),
    onBackground = Valtrea.Branco,
    surface = Color(0xFF0B1712),
    onSurface = Valtrea.Branco,
    surfaceContainer = Color(0xFF13221B),
    surfaceContainerHigh = Color(0xFF1A2C23),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF374151),
)

private val Claro = lightColorScheme(
    primary = Valtrea.VerdeSecundario,
    onPrimary = Valtrea.Branco,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Valtrea.VerdeEscuro,
    secondary = Valtrea.VerdePrincipal,
    secondaryContainer = Color(0xFFDCFCE7),
    onSecondaryContainer = Valtrea.VerdeEscuro,
    background = Valtrea.Branco,
    onBackground = Valtrea.Grafite,
    surface = Valtrea.Branco,
    onSurface = Valtrea.Grafite,
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerHigh = Valtrea.CinzaClaro,
    onSurfaceVariant = Valtrea.Cinza,
    outline = Color(0xFFD1D5DB),
)

@Composable
fun ValtreaTheme(escuro: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (escuro) Escuro else Claro, content = content)
}

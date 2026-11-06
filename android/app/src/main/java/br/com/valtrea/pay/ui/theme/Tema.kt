package br.com.valtrea.pay.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import br.com.valtrea.pay.R

// Paleta oficial do guia de marca Valtrea.
object Valtrea {
    val VerdePrincipal = Color(0xFF22C55E)
    val VerdeSecundario = Color(0xFF16A34A)
    // Verde mais escuro para texto e botões no tema claro: 5:1 com branco (WCAG AA). O #16A34A dava 3,3:1.
    val VerdeAcessivel = Color(0xFF15803D)
    val VerdeEscuro = Color(0xFF0F3D2E)
    val Grafite = Color(0xFF111827)
    val Cinza = Color(0xFF6B7280)
    val CinzaClaro = Color(0xFFE5E7EB)
    val Branco = Color(0xFFFFFFFF)

    // Fundo do cartão de saldo, com texto branco por cima: os dois tons passam de 4,5:1 com branco.
    val Gradiente = Brush.linearGradient(listOf(VerdeAcessivel, Color(0xFF166534)))
}

internal val Escuro = darkColorScheme(
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
    surfaceVariant = Color(0xFF13221B),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF374151),
)

internal val Claro = lightColorScheme(
    primary = Valtrea.VerdeAcessivel,
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
    // Sem isso o Material usa um lilás de fundo em alguns componentes.
    surfaceVariant = Color(0xFFF3F4F6),
    // Cinza mais escuro que o da marca: o #6B7280 ficava abaixo de 4,5:1 sobre os fundos cinza (teste de contraste).
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D5DB),
)

// Fonte oficial do guia de marca: Inter (licença SIL OFL 1.1, veja android/licencas/OFL-Inter.txt),
// em quatro pesos fixos e só com os caracteres usados em português, para o app ficar leve.
private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val Padrao = Typography()

internal val Tipografia = Typography(
    displayLarge = Padrao.displayLarge.copy(fontFamily = Inter),
    displayMedium = Padrao.displayMedium.copy(fontFamily = Inter),
    displaySmall = Padrao.displaySmall.copy(fontFamily = Inter),
    headlineLarge = Padrao.headlineLarge.copy(fontFamily = Inter),
    headlineMedium = Padrao.headlineMedium.copy(fontFamily = Inter),
    headlineSmall = Padrao.headlineSmall.copy(fontFamily = Inter),
    titleLarge = Padrao.titleLarge.copy(fontFamily = Inter),
    titleMedium = Padrao.titleMedium.copy(fontFamily = Inter),
    titleSmall = Padrao.titleSmall.copy(fontFamily = Inter),
    bodyLarge = Padrao.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = Padrao.bodyMedium.copy(fontFamily = Inter),
    bodySmall = Padrao.bodySmall.copy(fontFamily = Inter),
    labelLarge = Padrao.labelLarge.copy(fontFamily = Inter),
    labelMedium = Padrao.labelMedium.copy(fontFamily = Inter),
    labelSmall = Padrao.labelSmall.copy(fontFamily = Inter),
)

@Composable
fun ValtreaTheme(escuro: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (escuro) Escuro else Claro, typography = Tipografia, content = content)
}

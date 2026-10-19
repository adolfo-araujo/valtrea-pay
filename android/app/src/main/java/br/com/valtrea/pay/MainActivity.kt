package br.com.valtrea.pay

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.com.valtrea.pay.data.EstadoSessao
import br.com.valtrea.pay.data.LinkPagamento
import br.com.valtrea.pay.ui.confirmar.ConfirmarEmailScreen
import br.com.valtrea.pay.ui.entrar.EntrarScreen
import br.com.valtrea.pay.ui.inicio.InicioScreen
import br.com.valtrea.pay.ui.pagar.PagarScreen
import br.com.valtrea.pay.ui.perfil.PerfilScreen
import br.com.valtrea.pay.ui.receber.ReceberScreen
import br.com.valtrea.pay.ui.theme.ValtreaTheme
import kotlinx.serialization.Serializable

@Serializable data object RotaInicio
@Serializable data object RotaPagar
@Serializable data object RotaPerfil
@Serializable data object RotaConfirmarEmail
@Serializable data object RotaReceber

private data class Aba(val rota: Any, val rotulo: String, val icone: ImageVector)

private val abas = listOf(
    Aba(RotaInicio, "Início", Icons.Default.Home),
    Aba(RotaPagar, "Pagar", Icons.Default.Send),
    Aba(RotaPerfil, "Perfil", Icons.Default.Person),
)

class MainActivity : ComponentActivity() {

    /** Link valtrea://pagar/... que abriu o app. Fica guardado até a pessoa estar logada. */
    private var linkPendente by mutableStateOf<LinkPagamento?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val sessao = (application as ValtreaApp).sessao
        installSplashScreen().setKeepOnScreenCondition { sessao.estado.value == EstadoSessao.Carregando }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pedirAcessoARedeLocalEmDebug()
        if (savedInstanceState == null) receberLink(intent)
        setContent {
            ValtreaTheme {
                val estado by sessao.estado.collectAsStateWithLifecycle()
                // O Surface pinta o fundo do tema e define a cor do texto (claro no modo escuro, escuro no claro).
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (estado) {
                        EstadoSessao.Carregando -> Unit
                        EstadoSessao.Deslogado -> EntrarScreen()
                        EstadoSessao.Logado -> AppLogado(linkPendente) { linkPendente = null }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receberLink(intent)
    }

    private fun receberLink(intent: Intent?) {
        intent?.dataString?.let(LinkPagamento::ler)?.let { linkPendente = it }
    }

    /** A API de desenvolvimento roda no computador, que o Android 17+ trata como rede local. */
    private fun pedirAcessoARedeLocalEmDebug() {
        val permissao = "android.permission.ACCESS_LOCAL_NETWORK"
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 37 &&
            checkSelfPermission(permissao) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(permissao), 0)
        }
    }
}

@Composable
private fun AppLogado(link: LinkPagamento?, aoUsarLink: () -> Unit) {
    val nav = rememberNavController()
    val entradaAtual by nav.currentBackStackEntryAsState()

    fun irPara(rota: Any) {
        // O Início é a base da pilha: voltar para ele fecha o que estiver por cima (Receber, Confirmar e-mail).
        // Restaurar o estado aqui reabriria essas telas e a pessoa não conseguiria sair delas.
        if (rota == RotaInicio) {
            if (!nav.popBackStack(RotaInicio, inclusive = false)) nav.navigate(RotaInicio)
            return
        }
        nav.navigate(rota) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun voltar() {
        nav.popBackStack()
    }

    LaunchedEffect(link) { if (link != null) irPara(RotaPagar) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                abas.forEach { aba ->
                    NavigationBarItem(
                        selected = entradaAtual?.destination?.hasRoute(aba.rota::class) == true,
                        onClick = { irPara(aba.rota) },
                        icon = { Icon(aba.icone, contentDescription = null) },
                        label = { Text(aba.rotulo) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = RotaInicio) {
            composable<RotaInicio> {
                InicioScreen(
                    aoPagar = { irPara(RotaPagar) },
                    aoReceber = { nav.navigate(RotaReceber) },
                    aoConfirmarEmail = { nav.navigate(RotaConfirmarEmail) },
                    contentPadding = padding,
                )
            }
            composable<RotaReceber> { ReceberScreen(aoVoltar = { voltar() }, contentPadding = padding) }
            composable<RotaConfirmarEmail> {
                ConfirmarEmailScreen(aoConcluir = { voltar() }, aoVoltar = { voltar() }, contentPadding = padding)
            }
            composable<RotaPagar> {
                PagarScreen(
                    aoConcluir = { irPara(RotaInicio) },
                    contentPadding = padding,
                    link = link,
                    aoUsarLink = aoUsarLink,
                )
            }
            composable<RotaPerfil> { PerfilScreen(contentPadding = padding) }
        }
    }
}

package br.com.valtrea.pay.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.valtrea.pay.ValtreaApp

/** Cria um ViewModel com acesso às dependências do [ValtreaApp]. */
@Composable
inline fun <reified VM : ViewModel> valtreaViewModel(crossinline criar: (ValtreaApp) -> VM): VM {
    val app = LocalContext.current.applicationContext as ValtreaApp
    return viewModel(factory = viewModelFactory { initializer { criar(app) } })
}

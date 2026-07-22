package com.ltvreader.ui.screens.voices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ltvreader.R
import com.ltvreader.app.AppContainer
import com.ltvreader.tts.VoiceInfo
import com.ltvreader.ui.components.LTVScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Список голосов по всем доступным движкам.
 *
 * Прямой порт `MainWindow._build_voices_page()` (~140 строк).
 */
@Composable
fun VoicesScreen(
    nav: NavController,
    vm: VoicesViewModel = viewModel(factory = VoicesViewModelFactory(LocalContext.current)),
) {
    val state by vm.state.collectAsState()
    LTVScaffold(
        nav = nav,
        title = stringResource(R.string.nav_voices),
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.byEngine.entries.toList(), key = { it.key }) { (engineId, voices) ->
                    Text("$engineId (${voices.size})", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    voices.forEach { v -> VoiceCard(v) }
                }
            }
        }
    }
}

@Composable
private fun VoiceCard(v: VoiceInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(v.displayName.ifBlank { v.id }, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            Text("${v.language} · ${v.gender}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            if (v.previewUrl != null) Text("preview available", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
    }
}

data class VoicesState(
    val byEngine: Map<String, List<VoiceInfo>> = emptyMap(),
    val loading: Boolean = false,
)

class VoicesViewModel(private val context: android.content.Context) : ViewModel() {
    private val registry = AppContainer.registry(context)
    private val _state = MutableStateFlow(VoicesState())
    val state: StateFlow<VoicesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val all = registry.allEngineInfos()
            val map = mutableMapOf<String, List<VoiceInfo>>()
            for (e in all) {
                runCatching { registry.get(e.id).listVoices() }
                    .onSuccess { map[e.id] = it }
            }
            _state.update { it.copy(byEngine = map, loading = false) }
        }
    }
}

class VoicesViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = VoicesViewModel(context) as T
}

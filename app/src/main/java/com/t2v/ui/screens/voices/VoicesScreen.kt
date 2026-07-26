package com.t2v.ui.screens.voices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.t2v.R
import com.t2v.app.AppContainer
import com.t2v.data.SettingsRepository
import com.t2v.tts.VoiceInfo
import com.t2v.ui.components.LTVScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.byEngine.forEach { (engineId, voices) ->
                    item(key = engineId) {
                        Text("$engineId (${voices.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    voices.forEach { v ->
                        item(key = "${engineId}-${v.id}") {
                            VoiceCard(
                                voice = v,
                                selected = state.selectedVoiceId == v.id,
                                onSelect = { vm.selectVoice(v) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceCard(
    voice: VoiceInfo,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(voice.displayName.ifBlank { voice.id }, style = MaterialTheme.typography.titleSmall)
            Text("${voice.language} · ${voice.gender}", style = MaterialTheme.typography.bodySmall)
            if (voice.previewUrl != null) Text("preview available", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSelect, enabled = !selected) {
                    Text(if (selected) "Selected" else "Select")
                }
            }
        }
    }
}

data class VoicesState(
    val byEngine: Map<String, List<VoiceInfo>> = emptyMap(),
    val loading: Boolean = false,
    val selectedVoiceId: String = "",
    val error: String? = null,
)

class VoicesViewModel(private val context: android.content.Context) : ViewModel() {
    private val registry = AppContainer.registry(context)
    private val settings = AppContainer.settings(context)
    private val _state = MutableStateFlow(VoicesState())
    val state: StateFlow<VoicesState> = _state.asStateFlow()
    init {
        viewModelScope.launch {
            settings.flow.collect { value ->
                _state.update { it.copy(selectedVoiceId = value.voiceId) }
            }
        }
        loadVoices()
    }

    private fun loadVoices() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val all = registry.allEngineInfos()
            val map = mutableMapOf<String, List<VoiceInfo>>()
            for (e in all) {
                runCatching { registry.get(e.id).listVoices() }.onSuccess { map[e.id] = it }
            }
            _state.update {
                it.copy(
                    byEngine = map,
                    loading = false,
                )
            }
        }
    }

    fun selectVoice(voice: VoiceInfo) {
        viewModelScope.launch {
            settings.update {
                it[SettingsRepository.Keys.VOICE_ID] = voice.id
                it[SettingsRepository.Keys.TTS_ENGINE] = voice.engineId
            }
        }
    }

}

class VoicesViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = VoicesViewModel(context) as T
}

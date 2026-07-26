package com.ltvreader.ui.screens.voices

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ltvreader.R
import com.ltvreader.app.AppContainer
import com.ltvreader.data.SettingsRepository
import com.ltvreader.server.EngineHostClient
import com.ltvreader.tts.VoiceInfo
import com.ltvreader.ui.components.LTVScaffold
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
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(vm::openCloneDialog)
    }
    LTVScaffold(
        nav = nav,
        title = stringResource(R.string.nav_voices),
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.canClone) {
                Button(
                    enabled = !state.cloning,
                    onClick = { audioPicker.launch("audio/*") },
                ) {
                    Text("Clone voice")
                }
            }
            state.cloneAudioUri?.let { uri ->
                CloneVoiceDialog(
                    cloning = state.cloning,
                    onDismiss = vm::closeCloneDialog,
                    onCreate = { name, transcript, language, modelId ->
                        vm.createClone(uri, name, transcript, language, modelId)
                    },
                )
            }
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
                                downloading = state.downloadingVoiceId == v.id,
                                onSelect = { vm.selectVoice(v) },
                                onDownload = { vm.downloadVoice(v) },
                                onDelete = { vm.deleteClone(v) },
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
    downloading: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(voice.displayName.ifBlank { voice.id }, style = MaterialTheme.typography.titleSmall)
            Text("${voice.language} · ${voice.gender}", style = MaterialTheme.typography.bodySmall)
            if (voice.previewUrl != null) Text("preview available", style = MaterialTheme.typography.bodySmall)
            voice.downloadModelId?.let {
                Text(
                    "Included in model • ${formatVoiceSize(voice.downloadSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSelect, enabled = !selected) {
                    Text(if (selected) "Selected" else "Select")
                }
                if (voice.downloadModelId != null) {
                    Button(onClick = onDownload, enabled = !downloading) {
                        if (downloading) {
                            CircularProgressIndicator()
                        } else {
                            Text("Download voice model")
                        }
                    }
                }
                if (voice.isCloned) {
                    OutlinedButton(onClick = onDelete) {
                        Text("Delete clone")
                    }
                }
            }
        }
    }
}

@Composable
private fun CloneVoiceDialog(
    cloning: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("Auto") }
    var modelId by remember { mutableStateOf("Qwen/Qwen3-TTS-12Hz-0.6B-Base") }
    val models = listOf(
        "Qwen/Qwen3-TTS-12Hz-0.6B-Base" to "Qwen Base 0.6B • ~1.8 GB",
        "Qwen/Qwen3-TTS-12Hz-1.7B-Base" to "Qwen Base 1.7B • ~4.2 GB",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clone voice") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Use a clean 3–15 second recording and enter its exact transcript.")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Voice name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = transcript,
                    onValueChange = { transcript = it },
                    label = { Text("Exact reference transcript") },
                )
                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Language or Auto") },
                    singleLine = true,
                )
                models.forEach { (id, label) ->
                    Row {
                        RadioButton(selected = modelId == id, onClick = { modelId = id })
                        Text(label, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !cloning && name.isNotBlank() && transcript.isNotBlank(),
                onClick = { onCreate(name, transcript, language, modelId) },
            ) {
                Text(if (cloning) "Cloning…" else "Create clone")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !cloning) {
                Text("Cancel")
            }
        },
    )
}

data class VoicesState(
    val byEngine: Map<String, List<VoiceInfo>> = emptyMap(),
    val loading: Boolean = false,
    val selectedVoiceId: String = "",
    val downloadingVoiceId: String = "",
    val error: String? = null,
    val canClone: Boolean = false,
    val cloneAudioUri: Uri? = null,
    val cloning: Boolean = false,
)

class VoicesViewModel(private val context: android.content.Context) : ViewModel() {
    private val registry = AppContainer.registry(context)
    private val settings = AppContainer.settings(context)
    private var remoteHostUrl: String = ""
    private val _state = MutableStateFlow(VoicesState())
    val state: StateFlow<VoicesState> = _state.asStateFlow()
    init {
        viewModelScope.launch {
            settings.flow.collect { value ->
                remoteHostUrl = value.remoteHostUrl
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
                    canClone = all.any { engine -> engine.id == "remote:qwen" && engine.supportsCloning },
                )
            }
        }
    }

    fun openCloneDialog(uri: Uri) {
        _state.update { it.copy(cloneAudioUri = uri, error = null) }
    }

    fun closeCloneDialog() {
        if (!_state.value.cloning) _state.update { it.copy(cloneAudioUri = null) }
    }

    fun createClone(uri: Uri, name: String, transcript: String, language: String, modelId: String) {
        if (remoteHostUrl.isBlank()) {
            _state.update { it.copy(error = "Remote host URL is not configured") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(cloning = true, error = null) }
            runCatching {
                val audio = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Cannot read reference audio")
                require(audio.size <= 50 * 1024 * 1024) { "Reference audio must be smaller than 50 MB" }
                EngineHostClient(remoteHostUrl.trimEnd('/')).createVoiceClone(
                    name = name,
                    transcript = transcript,
                    language = language,
                    modelId = modelId,
                    fileName = "reference.wav",
                    audio = audio,
                )
            }.onSuccess {
                _state.update { it.copy(cloning = false, cloneAudioUri = null) }
                loadVoices()
            }.onFailure { error ->
                _state.update { it.copy(cloning = false, error = error.message) }
            }
        }
    }

    fun deleteClone(voice: VoiceInfo) {
        if (!voice.isCloned || !voice.id.startsWith("clone:")) return
        viewModelScope.launch {
            runCatching {
                EngineHostClient(remoteHostUrl.trimEnd('/'))
                    .deleteVoiceClone(voice.id.removePrefix("clone:"))
            }.onSuccess {
                loadVoices()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message) }
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

    fun downloadVoice(voice: VoiceInfo) {
        val modelId = voice.downloadModelId ?: return
        if (remoteHostUrl.isBlank()) {
            _state.update { it.copy(error = "Remote host URL is not configured") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(downloadingVoiceId = voice.id, error = null) }
            runCatching {
                EngineHostClient(remoteHostUrl.trimEnd('/')).downloadVoiceModel(modelId)
            }.onSuccess {
                _state.update { it.copy(downloadingVoiceId = "") }
            }.onFailure { error ->
                _state.update { it.copy(downloadingVoiceId = "", error = error.message) }
            }
        }
    }
}

private fun formatVoiceSize(bytes: Long): String =
    if (bytes <= 0) "size unknown" else String.format(java.util.Locale.US, "%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0)

class VoicesViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = VoicesViewModel(context) as T
}

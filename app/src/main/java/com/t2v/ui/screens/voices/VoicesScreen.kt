package com.t2v.ui.screens.voices

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.t2v.tts.engines.ElevenLabsTtsEngine
import com.t2v.ui.components.LTVScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun VoicesScreen(
    nav: NavController,
    vm: VoicesViewModel = viewModel(factory = VoicesViewModelFactory(LocalContext.current)),
) {
    val state by vm.state.collectAsState()
    var showCloneDialog by remember { mutableStateOf(false) }
    var cloneName by remember { mutableStateOf("") }
    var cloneAudioUri by remember { mutableStateOf<Uri?>(null) }
    var consent by remember { mutableStateOf(false) }
    var showLocalDialog by remember { mutableStateOf(false) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        cloneAudioUri = uri
    }
    LTVScaffold(
        nav = nav,
        title = stringResource(R.string.nav_voices),
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { showCloneDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.voices_clone_cloud))
            }
            Button(
                onClick = { showLocalDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.voices_pick_local))
            }
            Text(
                "Русское клонирование доступно через ElevenLabs после добавления API-ключа. " +
                    "Локальные Piper-голоса скачиваются на экране «Модели».",
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.preferredLocalVoiceId.isNotBlank()) {
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("✓ Мой голос", style = MaterialTheme.typography.labelLarge)
                            Text(
                                state.preferredLocalVoiceLabel.ifBlank { state.preferredLocalVoiceId },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        OutlinedButton(onClick = { showLocalDialog = true }) {
                            Text("Сменить")
                        }
                    }
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            if (state.cloning) CircularProgressIndicator()
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
    if (showCloneDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.cloning) showCloneDialog = false },
            title = { Text("Клонировать голос") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Выберите чистую запись русской речи. Рекомендуется 30–120 секунд без музыки и шума.",
                    )
                    val elevenlabsKey by remember(context) {
                        androidx.compose.runtime.mutableStateOf(
                            settingsSnapshot(context, "elevenlabs"),
                        )
                    }
                    LaunchedEffect(Unit) {
                        elevenlabsKey.value = settingsSnapshot(context, "elevenlabs")
                    }
                    if (elevenlabsKey.value.isBlank()) {
                        Text(
                            "Сначала добавьте ElevenLabs API-ключ в Настройках → TTS-движки.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedTextField(
                        value = cloneName,
                        onValueChange = { cloneName = it },
                        label = { Text("Название голоса") },
                        singleLine = true,
                    )
                    OutlinedButton(onClick = {
                        val mime = arrayOf("audio/*")
                        try {
                            audioPicker.launch(mime)
                        } catch (e: android.content.ActivityNotFoundException) {
                            android.widget.Toast.makeText(
                                context,
                                "Не найден файловый менеджер для выбора аудио",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }) {
                        Text(if (cloneAudioUri == null) "Выбрать аудиозапись" else "Аудиозапись выбрана")
                    }
                    Row {
                        Checkbox(checked = consent, onCheckedChange = { consent = it })
                        Text(
                            "Я подтверждаю, что это мой голос или у меня есть явное разрешение владельца.",
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    Text(
                        "Запись будет отправлена в ElevenLabs. Функция требует API-ключ и может зависеть от тарифа.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    state.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                val dialogKey = remember(context) {
                    androidx.compose.runtime.mutableStateOf(
                        settingsSnapshot(context, "elevenlabs"),
                    )
                }
                LaunchedEffect(Unit) {
                    dialogKey.value = settingsSnapshot(context, "elevenlabs")
                }
                Button(
                    enabled = !state.cloning
                        && cloneName.isNotBlank()
                        && cloneAudioUri != null
                        && consent
                        && dialogKey.value.isNotBlank()
                        && !state.cloning,
                    onClick = {
                        vm.cloneElevenLabsVoice(cloneName, requireNotNull(cloneAudioUri)) {
                            showCloneDialog = false
                            cloneName = ""
                            cloneAudioUri = null
                            consent = false
                        }
                    },
                ) {
                    Text("Создать клон")
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !state.cloning,
                    onClick = { showCloneDialog = false },
                ) {
                    Text("Отмена")
                }
            },
        )
    }
    if (showLocalDialog) {
        LocalVoiceDialog(
            onDismiss = { showLocalDialog = false },
            onPick = { voice ->
                vm.pickLocalVoice(voice)
                showLocalDialog = false
            },
            current = state.preferredLocalVoiceId,
        )
    }
}

@Composable
private fun LocalVoiceDialog(
    onDismiss: () -> Unit,
    onPick: (com.t2v.tts.engines.PiperRussianTtsEngine.RussianVoice) -> Unit,
    current: String,
) {
    val voices = remember { com.t2v.tts.engines.PiperRussianTtsEngine.RUSSIAN_VOICES }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Локальный голос") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            ) {
                androidx.compose.foundation.lazy.items(voices) { voice ->
                    val isCurrent = voice.id == current
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(voice) },
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    voice.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isCurrent) Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                "${voice.language} · ${if (voice.gender == "female") "женский" else "мужской"} · ${formatBytes(voice.approximateSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val mb = bytes / 1_000_000.0
    return String.format(java.util.Locale.US, "%.1f МБ", mb)
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
    val cloning: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val preferredLocalVoiceId: String = "",
    val preferredLocalVoiceLabel: String = "",
)

class VoicesViewModel(private val context: android.content.Context) : ViewModel() {
    private val registry = AppContainer.registry(context)
    private val settings = AppContainer.settings(context)
    private val _state = MutableStateFlow(VoicesState())
    val state: StateFlow<VoicesState> = _state.asStateFlow()
    init {
        viewModelScope.launch {
            settings.flow.collect { value ->
                _state.update {
                    it.copy(
                        selectedVoiceId = value.voiceId,
                        preferredLocalVoiceId = value.preferredLocalVoice,
                        preferredLocalVoiceLabel = value.preferredLocalVoiceLabel,
                    )
                }
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

    /**
     * Mark a local Piper voice as the user's preferred one. Persists
     * immediately and surfaces a "✓ мой голос" badge in the UI.
     */
    fun pickLocalVoice(voice: com.t2v.tts.engines.PiperRussianTtsEngine.RussianVoice) {
        viewModelScope.launch {
            settings.update {
                it[SettingsRepository.Keys.PREFERRED_LOCAL_VOICE] = voice.id
                it[SettingsRepository.Keys.PREFERRED_LOCAL_VOICE_LABEL] = voice.displayName
                it[SettingsRepository.Keys.TTS_ENGINE] = "piper_ru"
                it[SettingsRepository.Keys.VOICE_ID] = voice.id
                it[SettingsRepository.Keys.LANGUAGE] = voice.language
            }
            _state.update {
                it.copy(message = "Локальный голос сохранён: ${voice.displayName}")
            }
        }
    }

    fun cloneElevenLabsVoice(name: String, audioUri: Uri, onSuccess: () -> Unit) {
        if (_state.value.cloning) return
        viewModelScope.launch {
            _state.update { it.copy(cloning = true, error = null, message = null) }
            runCatching {
                val engine = registry.get("elevenlabs") as? ElevenLabsTtsEngine
                    ?: error("Сначала добавьте ElevenLabs API-ключ в Настройках")
                val audioFile = copyAudioToCache(context, audioUri)
                try {
                    engine.cloneVoice(
                        name = name,
                        audioFile = audioFile,
                        mimeType = context.contentResolver.getType(audioUri) ?: "audio/mpeg",
                    )
                } finally {
                    audioFile.delete()
                }
            }.onSuccess { voiceId ->
                settings.update {
                    it[SettingsRepository.Keys.VOICE_ID] = voiceId
                    it[SettingsRepository.Keys.TTS_ENGINE] = "elevenlabs"
                }
                _state.update {
                    it.copy(
                        cloning = false,
                        selectedVoiceId = voiceId,
                        message = "Голос успешно клонирован и выбран",
                    )
                }
                onSuccess()
                loadVoices()
            }.onFailure { error ->
                _state.update {
                    it.copy(cloning = false, error = error.message ?: "Не удалось клонировать голос")
                }
            }
        }
    }

    private fun copyAudioToCache(context: Context, uri: Uri): File {
        val file = File.createTempFile("voice-sample-", ".audio", context.cacheDir)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Не удалось открыть аудиозапись" }
            file.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
        }
        return file
    }
}

class VoicesViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = VoicesViewModel(context) as T
}

private fun settingsSnapshot(context: android.content.Context, engineId: String): String {
    // Quick read through the running repository.
    val repo = com.t2v.app.AppContainer.settings(context)
    return repo.state.value.engines[engineId]?.get("apiKey").orEmpty()
}

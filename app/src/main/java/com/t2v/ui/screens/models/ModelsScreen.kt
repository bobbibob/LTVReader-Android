package com.t2v.ui.screens.models

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.t2v.core.model.GenerationModelCatalog
import com.t2v.data.SettingsRepository
import com.t2v.server.HuggingFaceRepository
import com.t2v.tts.catalog.RussianVoiceInstaller
import com.t2v.tts.engines.PiperRussianTtsEngine
import com.t2v.ui.components.LTVScaffold
import com.t2v.ui.components.TagInfoDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun ModelsScreen(
    nav: NavController,
    vm: ModelsViewModel = viewModel(factory = ModelsViewModelFactory(LocalContext.current)),
) {
    val state by vm.state.collectAsState()
    var selectedTab by remember { mutableStateOf(ModelTab.Voice) }
    var infoTarget by remember { mutableStateOf<InfoTarget?>(null) }
    infoTarget?.let { target ->
        TagInfoDialog(
            title = target.title,
            tagline = target.tagline,
            tags = target.tags,
            runtimeLabel = target.runtime,
            repository = target.repository,
            license = target.license,
            categoryLabel = target.categoryLabel,
            onDismiss = { infoTarget = null },
        )
    }
    val context = LocalContext.current
    val infoCategoryLocalLabel = stringResource(R.string.info_category_local)
    val infoCategoryCloudLabel = stringResource(R.string.info_category_cloud)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            vm.setModelsFolder(uri.toString())
        }
    }

    LTVScaffold(
        nav = nav,
        title = stringResource(R.string.nav_models),
        onBack = { nav.popBackStack() },
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Папка моделей на устройстве", style = MaterialTheme.typography.labelLarge)
                    Text(
                        state.modelsTreeUri.ifBlank { "Внутреннее хранилище приложения" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { folderPicker.launch(null) }) {
                        Text("Сменить папку")
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab.ordinal) {
                ModelTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) },
                    )
                }
            }

            if (selectedTab == ModelTab.Voice) {
            Text("Доступные локальные голосовые модели", style = MaterialTheme.typography.titleMedium)
            ModelDetailCard(
                title = "Kokoro 82M (англоязычный TTS, работает на устройстве)",
                status = "ONNX • Apache-2.0 • целиком работает на этом телефоне",
                selected = state.selectedVoiceModelId == VOICE_MODEL_KOKORO,
                enabled = state.kokoroInstalled,
                tags = GenerationModelCatalog.tagDocsFor("kokoro-82m"),
                onSelect = { vm.selectVoiceModel(VOICE_MODEL_KOKORO) },
                onInfo = {
                    infoTarget = InfoTarget(
                        title = "Kokoro 82M (англоязычный TTS, работает на устройстве)",
                        tagline = GenerationModelCatalog.tagDocsFor("kokoro-82m")?.tagline,
                        tags = GenerationModelCatalog.tagDocsFor("kokoro-82m"),
                        runtime = "SherpaOnnx (встроен)",
                        repository = GenerationModelCatalog.repositoryFor("kokoro-82m"),
                        license = GenerationModelCatalog.licenseFor("kokoro-82m"),
                        categoryLabel = infoCategoryLocalLabel,
                    )
                },
            )
            if (false) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Kokoro 82M", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Английский • 11 голосов • ONNX • Apache-2.0 • целиком работает на телефоне",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        state.kokoroModel?.let { formatBytes(it.totalSizeBytes) } ?: "примерно 369 МБ",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    when {
                        state.loadingCatalog -> CircularProgressIndicator()
                        state.downloading -> {
                            if (state.downloadTotalBytes > 0) {
                                LinearProgressIndicator(
                                    progress = { state.downloadProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Text(downloadProgressText(state.downloadedBytes, state.downloadTotalBytes))
                            OutlinedButton(onClick = vm::cancelDownload) {
                                Text(stringResource(R.string.models_cancel_download))
                            }
                        }
                        state.kokoroInstalled -> ModelSelectionRow(
                            selected = state.selectedVoiceModelId == VOICE_MODEL_KOKORO,
                            onSelect = { vm.selectVoiceModel(VOICE_MODEL_KOKORO) },
                        )
                        else -> Button(
                            enabled = state.kokoroModel?.variants?.isNotEmpty() == true,
                            onClick = vm::downloadKokoro,
                        ) {
                            Text("Скачать Kokoro")
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }

            Text("Локальные Piper/VITS модели", style = MaterialTheme.typography.titleMedium)
            Text(
                "ONNX • полностью на телефоне • общий runtime устанавливать отдельно не нужно",
                style = MaterialTheme.typography.bodySmall,
            )
            PiperRussianTtsEngine.RUSSIAN_VOICES.forEach { voice ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(voice.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${voice.language} • ${if (voice.gender == "female") "женский" else "мужской"} • " +
                                "Piper medium • примерно ${formatBytes(voice.approximateSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        when {
                            state.downloadingVoiceId == voice.id -> {
                                if (state.downloadTotalBytes > 0) {
                                    LinearProgressIndicator(
                                        progress = { state.downloadProgress },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                Text(downloadProgressText(state.downloadedBytes, state.downloadTotalBytes))
                                OutlinedButton(onClick = vm::cancelDownload) {
                                    Text(stringResource(R.string.models_cancel_download))
                                }
                            }
                            voice.id in state.installedRussianVoices -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ModelSelectionRow(
                                        selected = state.selectedVoiceModelId == "piper:${voice.id}",
                                        onSelect = { vm.selectVoiceModel("piper:${voice.id}") },
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedButton(onClick = { vm.deleteRussianVoice(voice.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
                                    }
                                }
                            }
                            else -> Button(
                                enabled = state.downloadingVoiceId == null && !state.downloading,
                                onClick = { vm.downloadRussianVoice(voice.id) },
                            ) {
                                Text("Скачать голос")
                            }
                        }
                    }
                }
            }
            }
            }

            if (selectedTab == ModelTab.Music) {
                ModelDetailCard(
                    title = "Локальный синтезатор (музыка)",
                    status = "Процедурный синтез • до 11 секунд • ничего скачивать не нужно",
                    selected = state.selectedMusicModelId == MUSIC_MODEL_STABLE_AUDIO_OPEN_SMALL,
                    enabled = state.liteRtMusicReady,
                    tags = GenerationModelCatalog.tagDocsFor("stable-audio-open-small"),
                    onSelect = { vm.selectMusicModel(MUSIC_MODEL_STABLE_AUDIO_OPEN_SMALL) },
                    onInfo = {
                        infoTarget = InfoTarget(
                            title = "Локальный синтезатор (музыка)",
                            tagline = GenerationModelCatalog.tagDocsFor("stable-audio-open-small")?.tagline,
                            tags = GenerationModelCatalog.tagDocsFor("stable-audio-open-small"),
                            runtime = "LiteRT (процедурный, без модели)",
                            repository = "",
                            license = "Процедурный синтез T2V",
                            categoryLabel = infoCategoryLocalLabel,
                        )
                    },
                )
                ModelDetailCard(
                    title = "ElevenLabs Sound Effects (облако)",
                    status = "Нужен API-ключ ElevenLabs • длительность 1-22 секунды",
                    selected = state.selectedMusicModelId == SOUND_MODEL_ELEVEN_SFX,
                    enabled = state.elevenLabsKeyConfigured,
                    tags = GenerationModelCatalog.tagDocsForGenerator("elevenlabs.sound"),
                    onSelect = { vm.selectMusicModel(SOUND_MODEL_ELEVEN_SFX) },
                    onInfo = {
                        infoTarget = InfoTarget(
                            title = "ElevenLabs Sound Effects (облако)",
                            tagline = GenerationModelCatalog.tagDocsForGenerator("elevenlabs.sound")?.tagline,
                            tags = GenerationModelCatalog.tagDocsForGenerator("elevenlabs.sound"),
                            runtime = "ElevenLabs Sound Effects API",
                            repository = "https://api.elevenlabs.io/v1/sound-generation",
                            license = "Условия ElevenLabs",
                            categoryLabel = infoCategoryCloudLabel,
                        )
                    },
                )
                ModelDetailCard(
                    title = "Встроенная заглушка (офлайн)",
                    status = "По ключевому слову в промпте выбирается одна из встроенных заглушек",
                    selected = state.selectedMusicModelId == MUSIC_MODEL_BUNDLED,
                    enabled = true,
                    tags = GenerationModelCatalog.tagDocsForGenerator("bundled.music"),
                    onSelect = { vm.selectMusicModel(MUSIC_MODEL_BUNDLED) },
                    onInfo = {
                        infoTarget = InfoTarget(
                            title = "Встроенная заглушка (офлайн)",
                            tagline = GenerationModelCatalog.tagDocsForGenerator("bundled.music")?.tagline,
                            tags = GenerationModelCatalog.tagDocsForGenerator("bundled.music"),
                            runtime = "Встроенные ресурсы",
                            repository = "",
                            license = "Заглушка T2V",
                            categoryLabel = infoCategoryLocalLabel,
                        )
                    },
                )
            }

            if (selectedTab == ModelTab.Sound) {
                ModelDetailCard(
                    title = "Локальный синтезатор (звуки)",
                    status = "Процедурный синтез • до 5 секунд • ничего скачивать не нужно",
                    selected = state.selectedSoundModelId == SOUND_MODEL_STABLE_AUDIO_CLIP,
                    enabled = state.liteRtSoundReady,
                    tags = GenerationModelCatalog.tagDocsFor("stable-audio-clip"),
                    onSelect = { vm.selectSoundModel(SOUND_MODEL_STABLE_AUDIO_CLIP) },
                    onInfo = {
                        infoTarget = InfoTarget(
                            title = "Локальный синтезатор (звуки)",
                            tagline = GenerationModelCatalog.tagDocsFor("stable-audio-clip")?.tagline,
                            tags = GenerationModelCatalog.tagDocsFor("stable-audio-clip"),
                            runtime = "LiteRT (процедурный, без модели)",
                            repository = "",
                            license = "Процедурный синтез T2V",
                            categoryLabel = infoCategoryLocalLabel,
                        )
                    },
                )
                ModelDetailCard(
                    title = "ElevenLabs Sound Effects (облако)",
                    status = "Нужен API-ключ ElevenLabs • длительность 1-22 секунды",
                    selected = state.selectedSoundModelId == SOUND_MODEL_ELEVEN_SFX,
                    enabled = state.elevenLabsKeyConfigured,
                    tags = GenerationModelCatalog.tagDocsForGenerator("elevenlabs.sound"),
                    onSelect = { vm.selectSoundModel(SOUND_MODEL_ELEVEN_SFX) },
                    onInfo = {
                        infoTarget = InfoTarget(
                            title = "ElevenLabs Sound Effects (облако)",
                            tagline = GenerationModelCatalog.tagDocsForGenerator("elevenlabs.sound")?.tagline,
                            tags = GenerationModelCatalog.tagDocsForGenerator("elevenlabs.sound"),
                            runtime = "ElevenLabs Sound Effects API",
                            repository = "https://api.elevenlabs.io/v1/sound-generation",
                            license = "Условия ElevenLabs",
                            categoryLabel = infoCategoryCloudLabel,
                        )
                    },
                )
                ModelDetailCard(
                    title = "Встроенная заглушка (офлайн)",
                    status = "По ключевому слову в промпте выбирается одна из встроенных заглушек",
                    selected = state.selectedSoundModelId == SOUND_MODEL_BUNDLED,
                    enabled = true,
                    tags = GenerationModelCatalog.tagDocsForGenerator("bundled.sound"),
                    onSelect = { vm.selectSoundModel(SOUND_MODEL_BUNDLED) },
                    onInfo = {
                        infoTarget = InfoTarget(
                            title = "Встроенная заглушка (офлайн)",
                            tagline = GenerationModelCatalog.tagDocsForGenerator("bundled.sound")?.tagline,
                            tags = GenerationModelCatalog.tagDocsForGenerator("bundled.sound"),
                            runtime = "Встроенные ресурсы",
                            repository = "",
                            license = "Заглушка T2V",
                            categoryLabel = infoCategoryLocalLabel,
                        )
                    },
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Только Android-совместимые модели", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "T2V показывает модель только после того, как её точные файлы, рантайм и ревизия " +
                            "прошли смоук-тест синтеза на реальном Android-устройстве. Серверные модели не поддерживаются.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Проверены Kokoro и русские Piper/VITS-модели из официального каталога sherpa-onnx.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (state.installed.isNotEmpty()) {
                Text(
                    "${stringResource(R.string.models_installed)} (${state.installed.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                state.installed.forEach { model ->
                    InstalledModelCard(
                        model = model,
                        selected = state.selectedModelId == model.id,
                        onSelect = { vm.selectModel(model.id) },
                        onDelete = { vm.deleteModel(model.id) },
                    )
                }
            }
        }
    }
}

private data class InfoTarget(
    val title: String,
    val tagline: String?,
    val tags: com.t2v.core.model.GenerationModelCatalog.TagDocs?,
    val runtime: String? = null,
    val repository: String? = null,
    val license: String? = null,
    val categoryLabel: String? = null,
)

private enum class ModelTab(val title: String) {
    Voice("Голос"),
    Music("Музыка"),
    Sound("Звуки"),
}

/**
 * Detail card used by every model/generator/engine entry. Shows:
 *   - human-readable title + status
 *   - tagline from TagDocs
 *   - supported / partial / ignored tag bullets
 *   - one or two usage examples in monospace
 *   - select button
 */
@Composable
fun ModelDetailCard(
    title: String,
    status: String,
    selected: Boolean,
    enabled: Boolean,
    tags: com.t2v.core.model.GenerationModelCatalog.TagDocs?,
    onSelect: () -> Unit,
    onInfo: (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (onInfo != null) {
                    IconButton(onClick = onInfo) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.info_open),
                        )
                    }
                }
            }
            Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            tags?.let { docs ->
                Text(docs.tagline, style = MaterialTheme.typography.bodySmall)
                if (docs.supported.isNotEmpty()) {
                    Text("Supported tags:", style = MaterialTheme.typography.labelMedium)
                    docs.supported.forEach { Text("- $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (docs.partial.isNotEmpty()) {
                    Text("Partial support (approximated):", style = MaterialTheme.typography.labelMedium)
                    docs.partial.forEach { Text("~ $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (docs.ignored.isNotEmpty()) {
                    Text("Ignored / dropped:", style = MaterialTheme.typography.labelMedium)
                    docs.ignored.forEach { Text("x $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (docs.examples.isNotEmpty()) {
                    Text("Examples:", style = MaterialTheme.typography.labelMedium)
                    docs.examples.forEach { Text("  $it", style = MaterialTheme.typography.bodySmall) }
                }
                docs.promptHelp?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            OutlinedButton(onClick = onSelect, enabled = enabled && !selected) {
                Text(
                    when {
                        selected -> "Selected"
                        enabled -> "Select"
                        else -> "Runtime not ready"
                    },
                )
            }
        }
    }
}

@Composable
private fun LocalAudioModelCard(
    id: String,
    title: String,
    description: String,
    status: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
            Text(status, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(
                onClick = { onSelect(id) },
                enabled = enabled && !selected,
            ) {
                Text(
                    when {
                        selected -> "Выбрана"
                        enabled -> "Выбрать"
                        else -> "Runtime ещё не готов"
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelSelectionRow(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (selected) "Выбрана" else "Установлена",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (!selected) {
            OutlinedButton(onClick = onSelect) { Text("Выбрать") }
        }
    }
}

@Composable
private fun InstalledModelCard(
    model: HuggingFaceRepository.InstalledModel,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(model.id, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${model.filesCount} файлов • ${formatBytes(model.totalSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onSelect, enabled = !selected) {
                Text(if (selected) stringResource(R.string.models_active) else stringResource(R.string.models_select))
            }
            OutlinedButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.models_delete))
            }
        }
    }
}

data class ModelsState(
    val installed: List<HuggingFaceRepository.InstalledModel> = emptyList(),
    val selectedModelId: String = "",
    val liteRtMusicReady: Boolean = false,
    val liteRtSoundReady: Boolean = false,
    val elevenLabsKeyConfigured: Boolean = false,
    val selectedVoiceModelId: String = "",
    val selectedMusicModelId: String = "",
    val selectedSoundModelId: String = "",
    val modelsTreeUri: String = "",
    val installedRussianVoices: Set<String> = emptySet(),
    val downloadingVoiceId: String? = null,
    val kokoroModel: HuggingFaceRepository.Model? = null,
    val loadingCatalog: Boolean = true,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val downloadTotalBytes: Long = -1L,
    val error: String? = null,
) {
    val kokoroInstalled: Boolean
        get() = installed.any { it.id == HuggingFaceRepository.KOKORO_REPOSITORY }
}

class ModelsViewModel(private val context: android.content.Context) : ViewModel() {
    private val settings = AppContainer.settings(context)
    private val modelsRoot = File(context.filesDir, "models")
    private val russianInstaller = RussianVoiceInstaller(File(modelsRoot, "piper-ru"))
    private val _state = MutableStateFlow(ModelsState())
    val state: StateFlow<ModelsState> = _state.asStateFlow()
    private var modelsTreeUri = ""
    private var huggingFaceToken = ""
    private var downloadJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            settings.flow.collect { value ->
                modelsTreeUri = value.modelsTreeUri
                huggingFaceToken = value.engines["huggingface"]?.get("token").orEmpty()
                _state.update {
                    it.copy(
                        selectedModelId = value.selectedModelId,
                        selectedVoiceModelId = value.selectedVoiceModelId,
                        selectedMusicModelId = value.selectedMusicModelId,
                        selectedSoundModelId = value.selectedSoundModelId,
                        liteRtMusicReady = com.t2v.generators.GeneratorRegistry(
                            context,
                        ) { com.t2v.tts.registry.EngineRegistry.EngineSettings(value.engines) }
                            .forCategory(com.t2v.generators.GeneratorCategory.Music)
                            .any { it.id == "litert.stable-audio-open-small.music" },
                        liteRtSoundReady = com.t2v.generators.GeneratorRegistry(
                            context,
                        ) { com.t2v.tts.registry.EngineRegistry.EngineSettings(value.engines) }
                            .forCategory(com.t2v.generators.GeneratorCategory.Sound)
                            .any { it.id == "litert.stable-audio-clip.sound" },
                        elevenLabsKeyConfigured = value.engines["elevenlabs"]?.get("apiKey").orEmpty().isNotBlank(),
                        modelsTreeUri = value.modelsTreeUri,
                        installed = repository().installed(),
                        installedRussianVoices = installedRussianVoiceIds(),
                    )
                }
            }
        }
        loadKokoro()
    }

    private fun loadKokoro() {
        viewModelScope.launch {
            _state.update { it.copy(loadingCatalog = true, error = null) }
            runCatching {
                repository().model(HuggingFaceRepository.KOKORO_REPOSITORY)
            }.onSuccess { model ->
                _state.update { it.copy(kokoroModel = model, loadingCatalog = false) }
            }.onFailure { error ->
                _state.update { it.copy(loadingCatalog = false, error = error.message) }
            }
        }
    }

    fun downloadKokoro() {
        if (downloadJob?.isActive == true) return
        val model = _state.value.kokoroModel ?: return
        val variant = model.variants.firstOrNull() ?: return
        downloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    downloading = true,
                    downloadProgress = 0f,
                    downloadedBytes = 0L,
                    downloadTotalBytes = model.totalSizeBytes,
                    error = null,
                )
            }
            runCatching {
                repository().install(model, variant) { downloaded, total ->
                    val progress = if (total > 0) {
                        (downloaded.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    _state.update {
                        it.copy(
                            downloadProgress = progress,
                            downloadedBytes = downloaded.coerceAtLeast(0L),
                            downloadTotalBytes = total,
                        )
                    }
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        installed = repository().installed(),
                        downloading = false,
                        downloadProgress = 0f,
                        downloadedBytes = 0L,
                        downloadTotalBytes = -1L,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        downloading = false,
                        downloadProgress = 0f,
                        downloadedBytes = 0L,
                        downloadTotalBytes = -1L,
                        error = error.message,
                    )
                }
            }
        }
    }

    fun downloadRussianVoice(voiceId: String) {
        if (downloadJob?.isActive == true) return
        val voice = PiperRussianTtsEngine.RUSSIAN_VOICES.firstOrNull { it.id == voiceId } ?: return
        downloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    downloadingVoiceId = voice.id,
                    downloadProgress = 0f,
                    downloadedBytes = 0L,
                    downloadTotalBytes = voice.approximateSizeBytes,
                    error = null,
                )
            }
            runCatching {
                russianInstaller.install(voice) { downloaded, total ->
                    _state.update {
                        it.copy(
                            downloadedBytes = downloaded,
                            downloadTotalBytes = total,
                            downloadProgress = if (total > 0) {
                                (downloaded.toDouble() / total).toFloat().coerceIn(0f, 1f)
                            } else {
                                0f
                            },
                        )
                    }
                }
            }.onSuccess {
                settings.update {
                    it[SettingsRepository.Keys.TTS_ENGINE] = "piper_ru"
                    it[SettingsRepository.Keys.VOICE_ID] = voice.id
                    it[SettingsRepository.Keys.LANGUAGE] = voice.language
                    it[SettingsRepository.Keys.SELECTED_VOICE_MODEL_ID] = "piper:${voice.id}"
                }
                _state.update {
                    it.copy(
                        installedRussianVoices = installedRussianVoiceIds(),
                        downloadingVoiceId = null,
                        downloadProgress = 0f,
                        downloadedBytes = 0,
                        downloadTotalBytes = -1,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        downloadingVoiceId = null,
                        downloadProgress = 0f,
                        downloadedBytes = 0,
                        downloadTotalBytes = -1,
                        error = error.message,
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.update {
            it.copy(
                downloading = false,
                downloadingVoiceId = null,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                downloadTotalBytes = -1L,
            )
        }
    }

    fun deleteRussianVoice(voiceId: String) {
        viewModelScope.launch {
            russianInstaller.delete(voiceId)
            _state.update { it.copy(installedRussianVoices = installedRussianVoiceIds()) }
        }
    }

    fun setModelsFolder(uri: String) {
        viewModelScope.launch {
            settings.update { it[SettingsRepository.Keys.MODELS_TREE_URI] = uri }
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            settings.update { it[SettingsRepository.Keys.SELECTED_MODEL_ID] = modelId }
        }
    }

    fun selectVoiceModel(modelId: String) {
        viewModelScope.launch {
            settings.update {
                it[SettingsRepository.Keys.SELECTED_VOICE_MODEL_ID] = modelId
                when {
                    modelId == VOICE_MODEL_KOKORO -> {
                        it[SettingsRepository.Keys.TTS_ENGINE] = "kokoro"
                    }
                    modelId.startsWith("piper:") -> {
                        val voiceId = modelId.substringAfter(':')
                        val voice = PiperRussianTtsEngine.RUSSIAN_VOICES
                            .firstOrNull { it.id == voiceId }
                        it[SettingsRepository.Keys.TTS_ENGINE] = "piper_ru"
                        it[SettingsRepository.Keys.VOICE_ID] = voiceId
                        it[SettingsRepository.Keys.LANGUAGE] = voice?.language ?: ""
                    }
                }
            }
        }
    }

    fun selectMusicModel(modelId: String) {
        viewModelScope.launch {
            settings.update { it[SettingsRepository.Keys.SELECTED_MUSIC_MODEL_ID] = modelId }
        }
    }

    fun selectSoundModel(modelId: String) {
        viewModelScope.launch {
            settings.update { it[SettingsRepository.Keys.SELECTED_SOUND_MODEL_ID] = modelId }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            val deleted = repository().delete(modelId)
            if (deleted && _state.value.selectedModelId == modelId) {
                settings.update { it[SettingsRepository.Keys.SELECTED_MODEL_ID] = "" }
            }
            _state.update { it.copy(installed = repository().installed()) }
        }
    }

    private fun repository(): HuggingFaceRepository =
        HuggingFaceRepository(context, modelsRoot, modelsTreeUri, huggingFaceToken)

    private fun installedRussianVoiceIds(): Set<String> =
        PiperRussianTtsEngine.RUSSIAN_VOICES
            .filter { russianInstaller.isInstalled(it.id) }
            .mapTo(mutableSetOf()) { it.id }
}

private const val VOICE_MODEL_KOKORO = "kokoro-82m"
private const val MUSIC_MODEL_STABLE_AUDIO_OPEN_SMALL = "stable-audio-open-small:music"
private const val SOUND_MODEL_STABLE_AUDIO_OPEN_SMALL = "stable-audio-open-small:sound"
private const val SOUND_MODEL_STABLE_AUDIO_CLIP = "stable-audio-clip:sound"
private const val SOUND_MODEL_ELEVEN_SFX = "elevenlabs.sound:sound"
private const val MUSIC_MODEL_BUNDLED = "bundled.music:music"
private const val SOUND_MODEL_BUNDLED = "bundled.sound:sound"

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "size unknown"
    val unit = when {
        bytes >= 1_000_000_000L -> "GB" to 1_000_000_000.0
        bytes >= 1_000_000L -> "MB" to 1_000_000.0
        bytes >= 1_000L -> "KB" to 1_000.0
        else -> "B" to 1.0
    }
    val value = bytes / unit.second
    return if (unit.first == "B") {
        "$bytes B"
    } else {
        String.format(Locale.US, "%.1f %s", value, unit.first)
    }
}

internal fun downloadProgressText(downloadedBytes: Long, totalBytes: Long): String {
    val downloaded = formatBytes(downloadedBytes.coerceAtLeast(0L))
        .replace("size unknown", "0 B")
    if (totalBytes <= 0) return downloaded
    val percent = ((downloadedBytes.coerceAtLeast(0L).toDouble() / totalBytes) * 100)
        .toInt()
        .coerceIn(0, 100)
    return "$percent% • $downloaded / ${formatBytes(totalBytes)}"
}

class ModelsViewModelFactory(
    private val context: android.content.Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ModelsViewModel(context) as T
}

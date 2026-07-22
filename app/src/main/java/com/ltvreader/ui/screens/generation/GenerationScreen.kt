package com.ltvreader.ui.screens.generation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.ltvreader.tts.EngineInfo
import com.ltvreader.tts.VoiceConfig
import com.ltvreader.ui.components.LTVScaffold
import com.ltvreader.worker.GenerationPipeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Экран генерации аудиокниги.
 *
 * Прямой порт части `MainWindow` с вкладкой "Generation" в
 * `main_window.py:3700-4500`. Управляет пайплайном, показывает прогресс.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerationScreen(
    nav: NavController,
    projectId: Long,
    vm: GenerationViewModel = viewModel(factory = GenerationViewModelFactory(LocalContext.current, projectId)),
) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LTVScaffold(
        nav = nav,
        title = stringResource(R.string.nav_generation),
        onBack = { nav.popBackStack() },
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(state.projectTitle, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text("${state.engines.size} engines available", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }

            // Выбор движка
            EngineSelector(
                engines = state.engines,
                selected = state.selectedEngine,
                onSelected = vm::setEngine,
            )

            // Скорость
            Text("Speed: ${"%.2f".format(state.speed)}")
            Slider(
                value = state.speed.toFloat(),
                onValueChange = { vm.setSpeed(it.toDouble()) },
                valueRange = 0.5f..2.0f,
                steps = 30,
            )

            // Прогресс
            if (state.progress.total > 0) {
                val frac = state.progress.done.toFloat() / state.progress.total.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${state.progress.done} / ${state.progress.total}")
                state.progress.currentSegment?.let {
                    Text("…$it", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }

            // Действия
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { scope.launch { vm.startGeneration(context) } },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isRunning,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("  " + stringResource(R.string.gen_start))
                }
                OutlinedButton(
                    onClick = { vm.cancel() },
                    modifier = Modifier.weight(1f),
                    enabled = state.isRunning,
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null)
                    Text("  " + stringResource(R.string.gen_cancel))
                }
            }

            // После генерации — кнопки к review и mix
            if (state.audiobookId != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Audiobook ready", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(state.progress.error ?: "OK")
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = {
                                nav.navigate(com.ltvreader.ui.navigation.Routes.review(state.audiobookId!!))
                            }) {
                                Text(stringResource(R.string.review_title))
                            }
                            Button(onClick = {
                                nav.navigate(com.ltvreader.ui.navigation.Routes.musicMix(state.audiobookId!!))
                            }) {
                                Text(stringResource(R.string.mix_title))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineSelector(
    engines: List<EngineInfo>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = engines.firstOrNull { it.id == selected }?.displayName ?: selected
    androidx.compose.material3.ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        ) {
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (e in engines) {
                DropdownMenuItem(text = { Text(e.displayName) }, onClick = {
                    expanded = false
                    onSelected(e.id)
                })
            }
        }
    }
}

data class GenState(
    val projectId: Long,
    val projectTitle: String,
    val engines: List<EngineInfo> = emptyList(),
    val selectedEngine: String = "kokoro",
    val speed: Double = 1.0,
    val isRunning: Boolean = false,
    val progress: GenerationPipeline.Progress = GenerationPipeline.Progress(),
    val audiobookId: Long? = null,
    val error: String? = null,
)

class GenerationViewModel(
    private val context: android.content.Context,
    private val projectId: Long,
) : ViewModel() {

    private val db = AppContainer.database(context)
    private val registry = AppContainer.registry(context)
    private val pipeline = AppContainer.pipeline(context)
    private val settings = AppContainer.settings(context)

    private val _state = MutableStateFlow(GenState(projectId = projectId, projectTitle = "Loading…"))
    val state: StateFlow<GenState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val project = db.projects().byId(projectId) ?: return@launch
            val voices = if (project.voiceConfigJson.isNotBlank()) {
                runCatching { Json.decodeFromString(VoiceConfig.serializer(), project.voiceConfigJson) }
                    .getOrDefault(VoiceConfig.EMPTY)
            } else VoiceConfig.EMPTY
            _state.update {
                it.copy(
                    projectTitle = project.title,
                    selectedEngine = project.ttsEngine,
                    speed = voices.speed,
                )
            }
            _state.update { it.copy(engines = registry.allEngineInfos()) }
        }
        // Подписка на прогресс пайплайна
        viewModelScope.launch {
            pipeline.progress.collect { p ->
                _state.update { it.copy(progress = p, isRunning = p.phase == GenerationPipeline.Progress.Phase.Processing || p.phase == GenerationPipeline.Progress.Phase.Synthesizing || p.phase == GenerationPipeline.Progress.Phase.Encoding) }
            }
        }
    }

    fun setEngine(id: String) = _state.update { it.copy(selectedEngine = id) }
    fun setSpeed(v: Double) = _state.update { it.copy(speed = v) }

    fun cancel() = viewModelScope.launch { pipeline.cancel() }

    suspend fun startGeneration(context: android.content.Context) {
        val s = settings.flow.first()
        val project = db.projects().byId(projectId) ?: return
        val voices = if (project.voiceConfigJson.isNotBlank()) {
            runCatching { Json.decodeFromString(VoiceConfig.serializer(), project.voiceConfigJson) }
                .getOrDefault(VoiceConfig.EMPTY)
        } else VoiceConfig.EMPTY
        val engineId = _state.value.selectedEngine
        val v = voices.copy(speed = _state.value.speed, voice = s.voiceId.ifEmpty { voices.voice }, lang = s.language.ifEmpty { voices.lang })

        // Создаём audiobook
        val audiobookId = db.audiobooks().upsert(
            com.ltvreader.data.AudiobookEntity(
                projectId = projectId,
                status = "running",
                startedAt = System.currentTimeMillis(),
            ),
        )

        val outputDir = java.io.File(context.filesDir, "audiobooks/$audiobookId")
        val result = pipeline.generate(
            projectId = projectId,
            audiobookId = audiobookId,
            rawText = project.rawText,
            voice = v,
            engineId = engineId,
            outputDir = outputDir,
        )

        // Обновляем статус
        val finalStatus = if (result.isSuccess) "completed" else "failed"
        db.audiobooks().update(
            com.ltvreader.data.AudiobookEntity(
                id = audiobookId,
                projectId = projectId,
                status = finalStatus,
                startedAt = System.currentTimeMillis(),
                completedAt = System.currentTimeMillis(),
                outputPath = result.getOrNull()?.absolutePath,
                errorMessage = result.exceptionOrNull()?.message,
            ),
        )
        _state.update { it.copy(audiobookId = audiobookId, error = result.exceptionOrNull()?.message) }
    }
}

class GenerationViewModelFactory(
    private val context: android.content.Context,
    private val projectId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = GenerationViewModel(context, projectId) as T
}

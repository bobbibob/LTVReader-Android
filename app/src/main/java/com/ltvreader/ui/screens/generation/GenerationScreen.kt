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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(state.projectTitle, style = MaterialTheme.typography.titleMedium)
                    Text("${state.engines.size} engines available", style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("Engine: ${state.selectedEngine}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.engines.take(4).forEach { e ->
                    OutlinedButton(onClick = { vm.setEngine(e.id) }) {
                        Text(e.displayName.take(15))
                    }
                }
            }

            Text("Speed: ${"%.2f".format(state.speed)}")
            Slider(
                value = state.speed.toFloat(),
                onValueChange = { vm.setSpeed(it.toDouble()) },
                valueRange = 0.5f..2.0f,
            )

            if (state.progress.total > 0) {
                val frac = state.progress.done.toFloat() / state.progress.total.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { frac.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${state.progress.done} / ${state.progress.total}")
            }

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

            if (state.audiobookId != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Audiobook ready", style = MaterialTheme.typography.titleMedium)
                        Text(state.progress.error ?: "OK")
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = {
                                nav.navigate(com.ltvreader.ui.navigation.Routes.review(state.audiobookId!!))
                            }) { Text(stringResource(R.string.review_title)) }
                            Button(onClick = {
                                nav.navigate(com.ltvreader.ui.navigation.Routes.musicMix(state.audiobookId!!))
                            }) { Text(stringResource(R.string.mix_title)) }
                        }
                    }
                }
            }
        }
    }
}

data class GenState(
    val projectId: Long = 0,
    val projectTitle: String = "Loading…",
    val engines: List<EngineInfo> = emptyList(),
    val selectedEngine: String = "kokoro",
    val speed: Double = 1.0,
    val isRunning: Boolean = false,
    val progress: GenerationPipeline.Progress = GenerationPipeline.Progress(),
    val audiobookId: Long? = null,
)

class GenerationViewModel(
    private val context: android.content.Context,
    private val projectId: Long,
) : ViewModel() {
    private val db = AppContainer.database(context)
    private val registry = AppContainer.registry(context)
    private val pipeline = AppContainer.pipeline(context)
    private val settings = AppContainer.settings(context)
    private val _state = MutableStateFlow(GenState(projectId = projectId))
    val state: StateFlow<GenState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val project = db.projects().byId(projectId)
            if (project != null) {
                val voices = if (project.voiceConfigJson.isNotBlank()) {
                    runCatching { Json.decodeFromString(VoiceConfig.serializer(), project.voiceConfigJson) }
                        .getOrDefault(VoiceConfig.EMPTY)
                } else VoiceConfig.EMPTY
                _state.update {
                    it.copy(
                        projectTitle = project.title,
                        selectedEngine = project.ttsEngine,
                        speed = voices.speed,
                        engines = registry.allEngineInfos(),
                    )
                }
            }
        }
        viewModelScope.launch {
            pipeline.progress.collect { p ->
                _state.update {
                    it.copy(
                        progress = p,
                        isRunning = p.phase == GenerationPipeline.Progress.Phase.Processing ||
                            p.phase == GenerationPipeline.Progress.Phase.Synthesizing ||
                            p.phase == GenerationPipeline.Progress.Phase.Encoding,
                    )
                }
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
        val v = voices.copy(
            speed = _state.value.speed,
            voice = s.voiceId.ifEmpty { voices.voice },
            lang = s.language.ifEmpty { voices.lang },
        )
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
        _state.update { it.copy(audiobookId = audiobookId) }
    }
}

class GenerationViewModelFactory(
    private val context: android.content.Context,
    private val projectId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = GenerationViewModel(context, projectId) as T
}

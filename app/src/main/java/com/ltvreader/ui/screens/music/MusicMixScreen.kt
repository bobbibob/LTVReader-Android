package com.ltvreader.ui.screens.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.ltvreader.core.audio.AudioEncoder
import com.ltvreader.core.audio.AudioMixSettings
import com.ltvreader.core.audio.AudioMixer
import com.ltvreader.core.audio.FFmpegBridge
import com.ltvreader.ui.components.LTVScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Экран микширования аудио.
 *
 * Прямой порт `audio_mix_preview_panel.py:AudioMixPreviewPanel` (3 474 строк)
 * — упрощённый: один голосовой файл + одна фоновая музыка + настройки микса.
 *
 * Полная версия (timeline, несколько клипов, SFX-события) — в следующих
 * итерациях, см. docs/ROADMAP.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicMixScreen(
    nav: NavController,
    audiobookId: Long,
    vm: MusicMixViewModel = viewModel(factory = MusicMixViewModelFactory(LocalContext.current, audiobookId)),
) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LTVScaffold(
        nav = nav,
        title = stringResource(R.string.mix_title),
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
                Column(Modifier.padding(12.dp)) {
                    Text("Voice: ${state.voicePath ?: "—"}")
                    Text("Music: ${state.musicPath ?: "— (none)"}")
                }
            }
            Text("Voice volume: ${state.settings.voiceVolumeDb.toInt()} dB")
            Slider(
                value = state.settings.voiceVolumeDb.toFloat(),
                onValueChange = { vm.setVoiceVolume(it.toDouble()) },
                valueRange = -20f..6f,
            )
            Text("Music volume: ${state.settings.musicVolumeDb.toInt()} dB")
            Slider(
                value = state.settings.musicVolumeDb.toFloat(),
                onValueChange = { vm.setMusicVolume(it.toDouble()) },
                valueRange = -30f..0f,
            )
            Text("Ducking: ${state.settings.duckingDb.toInt()} dB")
            Slider(
                value = state.settings.duckingDb.toFloat(),
                onValueChange = { vm.setDucking(it.toDouble()) },
                valueRange = -20f..0f,
            )
            Text("Fade in/out: ${state.settings.fadeInMs}/${state.settings.fadeOutMs} ms")
            Slider(
                value = state.settings.fadeInMs.toFloat(),
                onValueChange = { vm.setFadeIn(it.toInt()) },
                valueRange = 0f..5000f,
            )
            Slider(
                value = state.settings.fadeOutMs.toFloat(),
                onValueChange = { vm.setFadeOut(it.toInt()) },
                valueRange = 0f..5000f,
            )

            Button(
                onClick = { scope.launch { vm.render(context) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isRendering,
            ) {
                Text(stringResource(R.string.mix_render))
            }
            if (state.outputPath != null) {
                Text("✓ ${state.outputPath}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
    }
}

data class MusicMixState(
    val voicePath: String? = null,
    val musicPath: String? = null,
    val settings: AudioMixSettings = AudioMixSettings(voicePath = ""),
    val outputPath: String? = null,
    val isRendering: Boolean = false,
)

class MusicMixViewModel(
    private val context: android.content.Context,
    private val audiobookId: Long,
) : ViewModel() {

    private val db = AppContainer.database(context)
    private val _state = MutableStateFlow(MusicMixState())
    val state: StateFlow<MusicMixState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val audiobook = db.audiobooks().byId(audiobookId)
            _state.update {
                it.copy(
                    voicePath = audiobook?.outputPath,
                    settings = it.settings.copy(voicePath = audiobook?.outputPath.orEmpty()),
                )
            }
        }
    }

    fun setVoiceVolume(db: Double) = _state.update { it.copy(settings = it.settings.copy(voiceVolumeDb = db)) }
    fun setMusicVolume(db: Double) = _state.update { it.copy(settings = it.settings.copy(musicVolumeDb = db)) }
    fun setDucking(db: Double) = _state.update { it.copy(settings = it.settings.copy(duckingDb = db)) }
    fun setFadeIn(ms: Int) = _state.update { it.copy(settings = it.settings.copy(fadeInMs = ms)) }
    fun setFadeOut(ms: Int) = _state.update { it.copy(settings = it.settings.copy(fadeOutMs = ms)) }

    suspend fun render(context: android.content.Context) {
        val s = _state.value
        val voiceFile = s.voicePath?.let { File(it) } ?: return
        if (!voiceFile.exists()) return
        _state.update { it.copy(isRendering = true) }
        val outFile = File(context.filesDir, "audiobooks/$audiobookId/mix.mp3")
        outFile.parentFile?.mkdirs()
        if (s.musicPath == null) {
            // Просто конвертация + normalise
            FFmpegBridge.encode(voiceFile, outFile, "mp3")
        } else {
            FFmpegBridge.applyMusicDucking(
                voice = voiceFile,
                music = File(s.musicPath),
                output = outFile,
                musicVolumeDb = s.settings.musicVolumeDb,
                duckingDb = s.settings.duckingDb,
            )
        }
        _state.update { it.copy(outputPath = outFile.absolutePath, isRendering = false) }
    }
}

class MusicMixViewModelFactory(
    private val context: android.content.Context,
    private val audiobookId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MusicMixViewModel(context, audiobookId) as T
}

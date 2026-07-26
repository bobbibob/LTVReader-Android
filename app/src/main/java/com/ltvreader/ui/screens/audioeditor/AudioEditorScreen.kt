package com.ltvreader.ui.screens.audioeditor

import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ltvreader.app.AppContainer
import com.ltvreader.core.audio.AudioEditClip
import com.ltvreader.core.audio.AudioEditProject
import com.ltvreader.core.audio.AudioTrackKind
import com.ltvreader.core.audio.FFmpegBridge
import com.ltvreader.ui.components.LTVScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AudioEditorScreen(
    nav: NavController,
    audiobookId: Long,
    vm: AudioEditorViewModel = viewModel(
        factory = AudioEditorViewModelFactory(LocalContext.current, audiobookId),
    ),
) {
    val state by vm.state.collectAsState()
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let(vm::addMusic)
    }
    LTVScaffold(nav, "Audio editor", onBack = { nav.popBackStack() }) { padding: PaddingValues ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TrackEditor("Voice track", AudioTrackKind.Voice, state.project.voiceClips, vm)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Background music", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { musicPicker.launch("audio/*") }) { Text("Add clip") }
            }
            TrackEditor("Music track", AudioTrackKind.Music, state.project.musicClips, vm)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = vm::render,
                enabled = !state.rendering && state.project.voiceClips.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.rendering) "Rendering…" else "Render edited audio")
            }
            state.outputPath?.let { Text("Saved: $it") }
        }
    }
}

@Composable
private fun TrackEditor(
    title: String,
    kind: AudioTrackKind,
    clips: List<AudioEditClip>,
    vm: AudioEditorViewModel,
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (clips.isEmpty()) Text("No clips")
    clips.forEachIndexed { index, clip ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(File(clip.sourcePath).name.ifBlank { "Clip ${index + 1}" })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = clip.startMs.toString(),
                        onValueChange = { vm.setStart(kind, clip.id, it.toLongOrNull() ?: 0) },
                        label = { Text("Start, ms") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = clip.endMs.toString(),
                        onValueChange = { vm.setEnd(kind, clip.id, it.toLongOrNull() ?: 0) },
                        label = { Text("End, ms (0 = end)") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Speed: ${"%.2f".format(clip.speed)}×")
                Slider(
                    value = clip.speed.toFloat(),
                    onValueChange = { vm.setSpeed(kind, clip.id, it.toDouble()) },
                    valueRange = 0.5f..2f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.move(kind, index, -1) }, enabled = index > 0) {
                        Text("Earlier")
                    }
                    OutlinedButton(
                        onClick = { vm.move(kind, index, 1) },
                        enabled = index < clips.lastIndex,
                    ) { Text("Later") }
                    OutlinedButton(onClick = { vm.split(kind, clip.id) }) { Text("Split") }
                    OutlinedButton(onClick = { vm.delete(kind, clip.id) }) { Text("Delete") }
                }
            }
        }
    }
}

data class AudioEditorState(
    val project: AudioEditProject = AudioEditProject(),
    val rendering: Boolean = false,
    val outputPath: String? = null,
    val error: String? = null,
)

class AudioEditorViewModel(
    private val context: android.content.Context,
    private val audiobookId: Long,
) : ViewModel() {
    private val db = AppContainer.database(context)
    private val _state = MutableStateFlow(AudioEditorState())
    val state: StateFlow<AudioEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            db.audiobooks().byId(audiobookId)?.outputPath?.takeIf { File(it).isFile }?.let { path ->
                _state.update {
                    it.copy(project = it.project.copy(voiceClips = listOf(AudioEditClip(sourcePath = path))))
                }
            }
        }
    }

    fun addMusic(uri: Uri) = viewModelScope.launch {
        runCatching {
            val file = File(context.filesDir, "audiobooks/$audiobookId/editor-music-${System.currentTimeMillis()}")
            file.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
            } ?: error("Cannot read music")
            file
        }.onSuccess { file ->
            mutate(AudioTrackKind.Music) { it + AudioEditClip(sourcePath = file.absolutePath) }
        }.onFailure { error -> _state.update { it.copy(error = error.message) } }
    }

    fun setStart(kind: AudioTrackKind, id: String, value: Long) =
        updateClip(kind, id) { it.copy(startMs = value.coerceAtLeast(0)) }

    fun setEnd(kind: AudioTrackKind, id: String, value: Long) =
        updateClip(kind, id) { it.copy(endMs = value.coerceAtLeast(0)) }

    fun setSpeed(kind: AudioTrackKind, id: String, value: Double) =
        updateClip(kind, id) { it.copy(speed = value.coerceIn(0.5, 2.0)) }

    fun split(kind: AudioTrackKind, id: String) {
        val clips = clips(kind)
        val index = clips.indexOfFirst { it.id == id }
        if (index < 0) return
        val clip = clips[index]
        val splitAt = if (clip.endMs > clip.startMs) {
            clip.startMs + (clip.endMs - clip.startMs) / 2
        } else {
            _state.update { it.copy(error = "Set clip end before splitting") }
            return
        }
        val replacement = listOf(clip.copy(endMs = splitAt), clip.copy(id = java.util.UUID.randomUUID().toString(), startMs = splitAt))
        mutate(kind) { it.take(index) + replacement + it.drop(index + 1) }
    }

    fun move(kind: AudioTrackKind, index: Int, offset: Int) {
        mutate(kind) { source ->
            val target = index + offset
            if (index !in source.indices || target !in source.indices) return@mutate source
            source.toMutableList().apply { add(target, removeAt(index)) }
        }
    }

    fun delete(kind: AudioTrackKind, id: String) = mutate(kind) { it.filterNot { clip -> clip.id == id } }

    fun render() = viewModelScope.launch {
        val project = _state.value.project
        _state.update { it.copy(rendering = true, error = null) }
        runCatching {
            val root = File(context.filesDir, "audiobooks/$audiobookId")
            val voice = FFmpegBridge.renderEditedTrack(context, project.voiceClips, File(root, "edited-voice.wav"))
            if (project.musicClips.isEmpty()) {
                voice
            } else {
                val music = FFmpegBridge.renderEditedTrack(context, project.musicClips, File(root, "edited-music.wav"))
                FFmpegBridge.applyMusicDucking(
                    context,
                    voice,
                    music,
                    File(root, "edited-final.m4a"),
                    format = "m4a",
                )
            }
        }.onSuccess { output ->
            _state.update { it.copy(rendering = false, outputPath = output.absolutePath) }
        }.onFailure { error ->
            _state.update { it.copy(rendering = false, error = error.message) }
        }
    }

    private fun clips(kind: AudioTrackKind): List<AudioEditClip> =
        if (kind == AudioTrackKind.Voice) _state.value.project.voiceClips else _state.value.project.musicClips

    private fun mutate(kind: AudioTrackKind, transform: (List<AudioEditClip>) -> List<AudioEditClip>) {
        _state.update {
            val project = if (kind == AudioTrackKind.Voice) {
                it.project.copy(voiceClips = transform(it.project.voiceClips))
            } else {
                it.project.copy(musicClips = transform(it.project.musicClips))
            }
            it.copy(project = project, error = null)
        }
    }

    private fun updateClip(kind: AudioTrackKind, id: String, transform: (AudioEditClip) -> AudioEditClip) {
        mutate(kind) { clips -> clips.map { if (it.id == id) transform(it) else it } }
    }
}

class AudioEditorViewModelFactory(
    private val context: android.content.Context,
    private val audiobookId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AudioEditorViewModel(context, audiobookId) as T
}

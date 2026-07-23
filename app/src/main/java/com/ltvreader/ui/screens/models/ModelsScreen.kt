package com.ltvreader.ui.screens.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import com.ltvreader.R
import com.ltvreader.app.AppContainer
import com.ltvreader.server.ModelRepository
import com.ltvreader.server.ModelRepository.LocalModel
import com.ltvreader.server.ModelRepository.ModelInfo
import com.ltvreader.ui.components.LTVScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import android.net.Uri

/**
 * Экран управления моделями TTS.
 *
 * Показывает каталог доступных TTS-моделей (Kokoro, Piper, Chatterbox, Qwen3…)
 * с HuggingFace. Скачивание идёт через engine-host (Python-сервер),
 * который сам делает запрос к HF и отдаёт файлы по HTTPS.
 *
 * Скачанные модели лежат в `Context.filesDir/models/<repo_id>/`.
 */
@Composable
fun ModelsScreen(
    nav: NavController,
    vm: ModelsViewModel = viewModel(factory = ModelsViewModelFactory(LocalContext.current)),
) {
    val state by vm.state.collectAsState()
    LTVScaffold(
        nav = nav,
        title = "TTS Models",
        onBack = { nav.popBackStack() },
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Статус сервера
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (state.serverReachable)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (state.serverReachable) "✓ Engine host connected" else "✗ Engine host not reachable",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = state.serverUrl,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        OutlinedButton(onClick = { vm.refresh() }) {
                            Text("Refresh")
                        }
                    }
                }
            }

            // Установленные модели
            if (state.localModels.isNotEmpty()) {
                Text("Installed (${state.localModels.size})", style = MaterialTheme.typography.titleMedium)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(160.dp),
                ) {
                    items(state.localModels, key = { it.id }) { m ->
                        LocalModelCard(
                            m = m,
                            isSelected = state.selectedModelId == m.id,
                            onSelect = { vm.selectModel(m.id) },
                            onDelete = { vm.deleteModel(m.id) },
                        )
                    }
                }
                HorizontalDivider()
            }

            // Каталог
            Text("Available (${state.catalog.size})", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.catalog, key = { it.id }) { model ->
                    ModelCard(
                        model = model,
                        isInstalled = state.localModels.any { it.id == model.id },
                        isDownloading = state.downloadingId == model.id,
                        downloadProgress = state.downloadProgress,
                        onDownload = { vm.download(model) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    isInstalled: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    onDownload: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, style = MaterialTheme.typography.titleSmall)
                        if (isInstalled) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Installed",
                                modifier = Modifier.padding(start = 6.dp).size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        text = "${model.id} • ${model.size_mb} MB • ${model.engine}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!isInstalled) {
                    Button(
                        onClick = onDownload,
                        enabled = !isDownloading,
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Text("  Download")
                    }
                }
            }
            if (model.description.isNotBlank()) {
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (model.languages.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    model.languages.take(5).forEach { lang ->
                        AssistChip(
                            onClick = {},
                            label = { Text(lang, style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(),
                        )
                    }
                }
            }
            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { downloadProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LocalModelCard(
    m: LocalModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "  ${m.id}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    } else {
                        Text(m.id, style = MaterialTheme.typography.titleSmall)
                    }
                }
                Text(
                    text = "${m.total_size_mb} MB • ${m.files_count} files",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row {
                if (!isSelected) {
                    OutlinedButton(onClick = onSelect) { Text("Select") }
                } else {
                    Text("  ✓ Active", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

data class ModelsState(
    val serverReachable: Boolean = false,
    val serverUrl: String = "",
    val catalog: List<ModelInfo> = emptyList(),
    val localModels: List<LocalModel> = emptyList(),
    val selectedModelId: String = "",
    val loading: Boolean = false,
    val downloadingId: String = "",
    val downloadProgress: Float = 0f,
    val error: String? = null,
)

class ModelsViewModel(private val context: android.content.Context) : ViewModel() {
    private val settings = AppContainer.settings(context)
    private var modelsTreeUri: String = ""
    private val _state = MutableStateFlow(ModelsState(serverUrl = ""))
    val state: StateFlow<ModelsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.flow.collect { s ->
                modelsTreeUri = s.modelsTreeUri
                _state.update { it.copy(serverUrl = s.remoteHostUrl, selectedModelId = s.selectedModelId) }
                if (s.remoteHostEnabled && s.remoteHostUrl.isNotBlank()) {
                    val repo = ModelRepository(s.remoteHostUrl.trimEnd('/'))
                    _state.update { it.copy(serverReachable = repo.isReachable()) }
                    if (_state.value.serverReachable) {
                        val cat = repo.listModels()
                        val loc = repo.listLocalModels()
                        _state.update { it.copy(catalog = cat, localModels = loc) }
                    }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val s = _state.value
            if (s.serverUrl.isBlank()) return@launch
            _state.update { it.copy(loading = true) }
            val repo = ModelRepository(s.serverUrl.trimEnd('/'))
            val reachable = repo.isReachable()
            val cat = if (reachable) repo.listModels() else emptyList()
            val loc = if (reachable) repo.listLocalModels() else emptyList()
            _state.update {
                it.copy(
                    serverReachable = reachable,
                    catalog = cat,
                    localModels = loc,
                    loading = false,
                )
            }
        }
    }

    fun download(model: ModelInfo) {
        viewModelScope.launch {
            val s = _state.value
            if (s.serverUrl.isBlank()) return@launch
            val repo = ModelRepository(s.serverUrl.trimEnd('/'))
            _state.update { it.copy(downloadingId = model.id, downloadProgress = 0f) }
            try {
                // Для Kokoro — скачиваем на устройство напрямую (маленькая модель)
                if (model.engine == "kokoro" && model.files.isNotEmpty()) {
                    val modelsDir = File(context.filesDir, "models")
                    val modelDir = File(modelsDir, model.id.replace("/", "_"))
                    modelDir.mkdirs()
                    for ((idx, fname) in model.files.withIndex()) {
                        val out = File(modelDir, fname)
                        val result = repo.downloadFile(model.id, fname, out) { downloaded, total ->
                            val pct = if (total > 0) downloaded.toFloat() / total else 0f
                            val overall = (idx + pct) / model.files.size
                            _state.update { it.copy(downloadProgress = overall) }
                        }
                        copyToSelectedModelsFolder(result, model.id, fname)
                        // Копируем в kokoro-специфичную папку
                        if (fname.endsWith(".onnx")) {
                            val kokoroDir = File(context.filesDir, "voices/kokoro")
                            kokoroDir.mkdirs()
                            result.copyTo(File(kokoroDir, "kokoro.onnx"), overwrite = true)
                        }
                        if (fname == "voices.bin") {
                            val kokoroDir = File(context.filesDir, "voices/kokoro")
                            kokoroDir.mkdirs()
                            result.copyTo(File(kokoroDir, "voices.bin"), overwrite = true)
                        }
                    }
                } else {
                    // Для больших моделей — скачиваем на сервер
                    repo.requestServerDownload(model.id, model.files)
                }
                _state.update { it.copy(downloadingId = "", downloadProgress = 0f) }
                refresh()
            } catch (e: Throwable) {
                _state.update { it.copy(downloadingId = "", error = e.message) }
            }
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            settings.update { it[SettingsRepository.Keys.SELECTED_MODEL_ID] = modelId }
        }
    }

    /**
     * The selected folder is an Android document tree, not an unsafe filesystem path.
     * A runtime copy remains in app storage for ONNX Runtime, which requires a File path.
     */
    private fun copyToSelectedModelsFolder(source: File, modelId: String, fileName: String) {
        if (modelsTreeUri.isBlank()) return
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(modelsTreeUri)) ?: return
        val modelDir = tree.findFile(modelId.replace('/', '_')) ?: tree.createDirectory(modelId.replace('/', '_')) ?: return
        val target = modelDir.findFile(fileName) ?: modelDir.createFile("application/octet-stream", fileName) ?: return
        context.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
            source.inputStream().use { it.copyTo(output) }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            val s = _state.value
            if (s.serverUrl.isBlank()) return@launch
            val repo = ModelRepository(s.serverUrl.trimEnd('/'))
            repo.deleteLocal(modelId)
            refresh()
        }
    }
}

class ModelsViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ModelsViewModel(context) as T
}

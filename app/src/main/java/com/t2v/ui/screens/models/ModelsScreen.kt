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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.navigation.NavController
import com.t2v.R
import com.t2v.app.AppContainer
import com.t2v.data.SettingsRepository
import com.t2v.server.HuggingFaceRepository
import com.t2v.ui.components.LTVScaffold
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
    val context = LocalContext.current
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("On-device models folder", style = MaterialTheme.typography.labelLarge)
                    Text(
                        state.modelsTreeUri.ifBlank { "Internal app storage" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { folderPicker.launch(null) }) {
                        Text("Change folder")
                    }
                }
            }

            Text("Available on-device models", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Kokoro 82M", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "English • 11 voices • ONNX • Apache-2.0 • runs entirely on this phone",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        state.kokoroModel?.let { formatBytes(it.totalSizeBytes) } ?: "approximately 369 MB",
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
                        state.kokoroInstalled -> Text(
                            stringResource(R.string.models_active),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        else -> Button(
                            enabled = state.kokoroModel?.variants?.isNotEmpty() == true,
                            onClick = vm::downloadKokoro,
                        ) {
                            Text("Download Kokoro")
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Only Android-compatible models", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "T2V lists a model here only after its exact files, runtime and revision " +
                            "have passed synthesis tests on a real Android device. Server models are not supported.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Kokoro is the first verified catalog entry.", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (state.installed.isNotEmpty()) {
                Text(
                    "${stringResource(R.string.models_installed)} (${state.installed.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.installed, key = { it.id }) { model ->
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
                    "${model.filesCount} files • ${formatBytes(model.totalSizeBytes)}",
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
    val modelsTreeUri: String = "",
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
                        modelsTreeUri = value.modelsTreeUri,
                        installed = repository().installed(),
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

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.update {
            it.copy(
                downloading = false,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                downloadTotalBytes = -1L,
            )
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
}

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

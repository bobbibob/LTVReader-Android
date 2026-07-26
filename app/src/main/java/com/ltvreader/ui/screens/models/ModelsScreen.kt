package com.ltvreader.ui.screens.models

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.ltvreader.R
import com.ltvreader.app.AppContainer
import com.ltvreader.data.SettingsRepository
import com.ltvreader.server.HuggingFaceRepository
import com.ltvreader.ui.components.LTVScaffold
import kotlinx.coroutines.Job
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Models folder", style = MaterialTheme.typography.labelLarge)
                    Text(
                        state.modelsTreeUri.ifBlank { "Internal app storage" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        enabled = state.downloadingId.isBlank(),
                        onClick = { folderPicker.launch(null) },
                    ) {
                        Text("Change folder")
                    }
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text(stringResource(R.string.models_hf_repo)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = vm::search,
                    enabled = !state.loading && state.downloadingId.isBlank(),
                ) {
                    Text(stringResource(R.string.models_hf_search_action))
                }
                OutlinedButton(
                    onClick = vm::openExactRepository,
                    enabled = state.query.contains('/') &&
                        !state.loading &&
                        state.downloadingId.isBlank(),
                ) {
                    Text(stringResource(R.string.models_hf_open_repo))
                }
                if (state.loading) {
                    CircularProgressIndicator()
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            state.variantModel?.let { model ->
                VariantDialog(
                    model = model,
                    onDismiss = vm::closeVariants,
                    onDownload = { variant -> vm.download(model, variant) },
                )
            }

            if (state.installed.isNotEmpty()) {
                Text(
                    "${stringResource(R.string.models_installed)} (${state.installed.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                LazyColumn(
                    modifier = Modifier.weight(0.35f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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

            Text(
                "${stringResource(R.string.models_available)} (${state.catalog.size})",
                style = MaterialTheme.typography.titleMedium,
            )
            LazyColumn(
                modifier = Modifier.weight(0.65f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.catalog, key = { it.id }) { model ->
                    HuggingFaceModelCard(
                        model = model,
                        installed = state.installed.any { it.id == model.id },
                        downloading = state.downloadingId == model.id,
                        progress = state.downloadProgress,
                        onDownload = { vm.openVariants(model) },
                        onCancel = vm::cancelDownload,
                    )
                }
            }
        }
    }
}

@Composable
private fun VariantDialog(
    model: HuggingFaceRepository.Model,
    onDismiss: () -> Unit,
    onDownload: (HuggingFaceRepository.ModelVariant) -> Unit,
) {
    var selected by androidx.compose.runtime.remember(model.id) {
        androidx.compose.runtime.mutableStateOf(model.variants.firstOrNull())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(model.name) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(model.variants, key = { it.id }) { variant ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selected = variant },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected?.id == variant.id,
                                onClick = { selected = variant },
                            )
                            Column {
                                Text(variant.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${variant.format} • ${variant.quantization} • ${formatBytes(variant.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selected != null,
                onClick = { selected?.let(onDownload) },
            ) {
                Text(stringResource(R.string.models_download))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.models_cancel_download))
            }
        },
    )
}

@Composable
private fun HuggingFaceModelCard(
    model: HuggingFaceRepository.Model,
    installed: Boolean,
    downloading: Boolean,
    progress: Float,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, style = MaterialTheme.typography.titleSmall)
                        if (installed) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 6.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(model.id, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${model.compatibleFiles.size} TTS files • " +
                            "${formatBytes(model.totalSizeBytes)} • ${model.downloads} downloads",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!installed && !downloading) {
                    Button(
                        onClick = onDownload,
                        enabled = model.variants.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Text("  ${stringResource(R.string.models_download)}")
                    }
                }
            }
            if (downloading) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.models_cancel_download))
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
            if (selected) {
                Text(stringResource(R.string.models_active))
            } else {
                OutlinedButton(onClick = onSelect) {
                    Text(stringResource(R.string.models_select))
                }
            }
            OutlinedButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.models_delete))
            }
        }
    }
}

data class ModelsState(
    val query: String = "",
    val catalog: List<HuggingFaceRepository.Model> = emptyList(),
    val installed: List<HuggingFaceRepository.InstalledModel> = emptyList(),
    val selectedModelId: String = "",
    val loading: Boolean = false,
    val downloadingId: String = "",
    val downloadProgress: Float = 0f,
    val error: String? = null,
    val variantModel: HuggingFaceRepository.Model? = null,
    val modelsTreeUri: String = "",
)

class ModelsViewModel(private val context: android.content.Context) : ViewModel() {
    private val settings = AppContainer.settings(context)
    private val modelsRoot = File(context.filesDir, "models")
    private val _state = MutableStateFlow(ModelsState())
    val state: StateFlow<ModelsState> = _state.asStateFlow()
    private var downloadJob: Job? = null
    private var huggingFaceToken: String = ""
    private var modelsTreeUri: String = ""

    init {
        viewModelScope.launch {
            settings.flow.collect { value ->
                huggingFaceToken = value.engines["huggingface"]?.get("token").orEmpty()
                modelsTreeUri = value.modelsTreeUri
                _state.update {
                    it.copy(
                        selectedModelId = value.selectedModelId,
                        modelsTreeUri = value.modelsTreeUri,
                        installed = repository().installed(),
                    )
                }
            }
        }
        search()
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun setModelsFolder(uri: String) {
        viewModelScope.launch {
            settings.update { it[SettingsRepository.Keys.MODELS_TREE_URI] = uri }
        }
    }

    fun search() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                repository().search(_state.value.query)
            }.onSuccess { models ->
                _state.update { it.copy(catalog = models, loading = false) }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.message) }
            }
        }
    }

    fun openExactRepository() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                repository().model(_state.value.query.trim())
            }.onSuccess { model ->
                _state.update { it.copy(catalog = listOf(model), loading = false) }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.message) }
            }
        }
    }

    fun openVariants(model: HuggingFaceRepository.Model) {
        _state.update { it.copy(variantModel = model, error = null) }
    }

    fun closeVariants() {
        _state.update { it.copy(variantModel = null) }
    }

    fun download(
        model: HuggingFaceRepository.Model,
        variant: HuggingFaceRepository.ModelVariant,
    ) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    downloadingId = model.id,
                    downloadProgress = 0f,
                    error = null,
                    variantModel = null,
                )
            }
            runCatching {
                repository().install(model, variant) { downloaded, total ->
                    val progress = if (total > 0) downloaded.toFloat() / total else 0f
                    _state.update { it.copy(downloadProgress = progress) }
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        installed = repository().installed(),
                        downloadingId = "",
                        downloadProgress = 0f,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        downloadingId = "",
                        downloadProgress = 0f,
                        error = error.message,
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.update { it.copy(downloadingId = "", downloadProgress = 0f) }
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
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}

class ModelsViewModelFactory(
    private val context: android.content.Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ModelsViewModel(context) as T
}

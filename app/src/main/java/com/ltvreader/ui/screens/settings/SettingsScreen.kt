package com.ltvreader.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ltvreader.R
import com.ltvreader.app.AppContainer
import com.ltvreader.data.Settings
import com.ltvreader.data.SettingsRepository
import com.ltvreader.ui.components.LTVScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    nav: NavController,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(LocalContext.current)),
) {
    val state by vm.state.collectAsState()
    LTVScaffold(
        nav = nav,
        title = stringResource(R.string.nav_settings),
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_general))
            SliderSetting("Chunk size", state.settings.chunkSize.toFloat(), 500f..5000f) { vm.setChunkSize(it.toInt()) }
            SwitchSetting("Markup toolbar", state.settings.markupToolbar) { vm.setMarkupToolbar(it) }
            SwitchSetting("Syntax highlight", state.settings.syntaxHighlight) { vm.setSyntaxHighlight(it) }

            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_remote_host))
            OutlinedTextField(
                value = state.settings.remoteHostUrl,
                onValueChange = vm::setRemoteHostUrl,
                label = { Text("URL (e.g. http://192.168.1.10:8765)") },
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchSetting("Enable remote host", state.settings.remoteHostEnabled) { vm.setRemoteHostEnabled(it) }

            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_engines))
            PasswordField("OpenAI API Key", state.settings.engines["openai"]?.get("apiKey").orEmpty()) { v -> vm.setApiKey("openai", v) }
            PasswordField("ElevenLabs API Key", state.settings.engines["elevenlabs"]?.get("apiKey").orEmpty()) { v -> vm.setApiKey("elevenlabs", v) }
            PasswordField("Gemini API Key", state.settings.engines["gemini"]?.get("apiKey").orEmpty()) { v -> vm.setApiKey("gemini", v) }
            PasswordField("Azure Subscription Key", state.settings.engines["azure"]?.get("subscriptionKey").orEmpty()) { v -> vm.setApiKey("azure", v) }
            OutlinedTextField(
                value = state.settings.engines["azure"]?.get("region").orEmpty(),
                onValueChange = { vm.setApiKey("azure", it, "region") },
                label = { Text("Azure region (e.g. eastus)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SliderSetting(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label: ${value.toInt()}")
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchSetting(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
    )
}

data class SettingsUiState(val settings: Settings = Settings(
    uiLanguage = "en", outputDir = "output", ttsEngine = "kokoro", voiceId = "",
    language = "", speed = 1.0, splitMode = "safe_chunks", exportMode = "single",
    chunkSize = 2500, pauseBetweenBlocksMs = 350, pauseBetweenChaptersMs = 900,
    paragraphPauseMinMs = 450, paragraphPauseMaxMs = 900, markupToolbar = true, syntaxHighlight = true,
    remoteHostUrl = "", remoteHostEnabled = false, engines = emptyMap(),
))

class SettingsViewModel(private val context: android.content.Context) : ViewModel() {
    private val repo = AppContainer.settings(context)
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    init {
        viewModelScope.launch { repo.flow.collect { s -> _state.update { it.copy(settings = s) } } }
    }

    fun setChunkSize(v: Int) = viewModelScope.launch { repo.update { it[SettingsRepository.Keys.CHUNK_SIZE] = v } }
    fun setMarkupToolbar(v: Boolean) = viewModelScope.launch { repo.update { it[SettingsRepository.Keys.MARKUP_TOOLBAR] = v } }
    fun setSyntaxHighlight(v: Boolean) = viewModelScope.launch { repo.update { it[SettingsRepository.Keys.SYNTAX_HIGHLIGHT] = v } }
    fun setRemoteHostUrl(v: String) = viewModelScope.launch { repo.update { it[SettingsRepository.Keys.REMOTE_HOST_URL] = v } }
    fun setRemoteHostEnabled(v: Boolean) = viewModelScope.launch { repo.update { it[SettingsRepository.Keys.REMOTE_HOST_ENABLED] = v } }
    fun setApiKey(engine: String, v: String, field: String = "apiKey") = viewModelScope.launch {
        repo.update {
            when (engine) {
                "openai" -> it[SettingsRepository.Keys.OPENAI_KEY] = v
                "elevenlabs" -> it[SettingsRepository.Keys.ELEVENLABS_KEY] = v
                "gemini" -> it[SettingsRepository.Keys.GEMINI_KEY] = v
                "azure" -> if (field == "region") it[SettingsRepository.Keys.AZURE_REGION] = v
                else it[SettingsRepository.Keys.AZURE_KEY] = v
            }
        }
    }
}

class SettingsViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(context) as T
}

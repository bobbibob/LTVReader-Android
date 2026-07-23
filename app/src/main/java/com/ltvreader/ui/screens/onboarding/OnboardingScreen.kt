package com.ltvreader.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ltvreader.R
import com.ltvreader.app.AppContainer
import com.ltvreader.data.SettingsRepository
import com.ltvreader.ui.components.LTVScaffold
import com.ltvreader.ui.navigation.Routes
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/** First-run choice. It deliberately selects a mode, never a bundled model. */
@Composable
fun OnboardingScreen(
    nav: NavController,
    vm: OnboardingViewModel = viewModel(factory = OnboardingViewModelFactory(androidx.compose.ui.platform.LocalContext.current)),
) {
    LTVScaffold(nav = nav, title = stringResource(R.string.app_name)) { padding: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall)
            ModeCard(stringResource(R.string.onboarding_local), stringResource(R.string.onboarding_local_hint)) {
                vm.choose("local") { nav.navigate(Routes.Editor) { popUpTo(Routes.Onboarding) { inclusive = true } } }
            }
            ModeCard(stringResource(R.string.onboarding_cloud), stringResource(R.string.onboarding_cloud_hint)) {
                vm.choose("cloud") { nav.navigate(Routes.Editor) { popUpTo(Routes.Onboarding) { inclusive = true } } }
            }
        }
    }
}

@Composable private fun ModeCard(title: String, hint: String, onChoose: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onChoose) { Text(title) }
        }
    }
}

class OnboardingViewModel(private val repo: SettingsRepository) : ViewModel() {
    fun choose(mode: String, done: () -> Unit) = viewModelScope.launch {
        repo.update {
            it[SettingsRepository.Keys.TTS_MODE] = mode
            it[SettingsRepository.Keys.ONBOARDING_COMPLETED] = true
        }
        done()
    }
}

class OnboardingViewModelFactory(context: android.content.Context) : ViewModelProvider.Factory {
    private val repo = AppContainer.settings(context)
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = OnboardingViewModel(repo) as T
}

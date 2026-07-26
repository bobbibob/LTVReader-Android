package com.ltvreader.app

import android.app.Application
import com.ltvreader.core.text.TextProcessor
import com.ltvreader.data.AppDatabase
import com.ltvreader.data.SettingsRepository
import com.ltvreader.server.EngineHostClient
import com.ltvreader.tts.registry.EngineRegistry
import com.ltvreader.worker.GenerationPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application-класс. Создаёт DI-контейнер вручную (без Hilt — для краткости).
 */
class LTVApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val settingsRepo: SettingsRepository by lazy { SettingsRepository(this) }

    @Volatile private var engineSettings = EngineRegistry.EngineSettings()
    @Volatile var hostClient: EngineHostClient? = null
        private set

    val engineRegistry: EngineRegistry by lazy {
        EngineRegistry(
            appContext = this,
            settingsProvider = { engineSettings },
            hostClientProvider = { hostClient },
        )
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            settingsRepo.flow.collect { settings ->
                engineSettings = EngineRegistry.EngineSettings(settings.engines)
                val currentUrl = settings.remoteHostUrl.trimEnd('/')
                hostClient = if (settings.remoteHostEnabled && currentUrl.isNotBlank()) {
                    EngineHostClient(currentUrl)
                } else {
                    null
                }
                engineRegistry.closeAll()
            }
        }
    }

    val textProcessor: TextProcessor by lazy {
        // Берём из настроек, но по умолчанию 2500 символов.
        TextProcessor()
    }

    val pipeline: GenerationPipeline by lazy {
        GenerationPipeline(this, engineRegistry, textProcessor)
    }
}

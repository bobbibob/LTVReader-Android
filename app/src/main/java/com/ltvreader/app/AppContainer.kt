package com.ltvreader.app

import android.content.Context
import com.ltvreader.core.text.TextProcessor
import com.ltvreader.data.AppDatabase
import com.ltvreader.data.SettingsRepository
import com.ltvreader.server.EngineHostClient
import com.ltvreader.tts.registry.EngineRegistry
import com.ltvreader.worker.GenerationPipeline

/**
 * Простой DI-контейнер, доступный из любого места как (context.applicationContext as LTVApplication).
 */
object AppContainer {
    fun database(ctx: Context): AppDatabase =
        (ctx.applicationContext as LTVApplication).database
    fun settings(ctx: Context): SettingsRepository =
        (ctx.applicationContext as LTVApplication).settingsRepo
    fun registry(ctx: Context): EngineRegistry =
        (ctx.applicationContext as LTVApplication).engineRegistry
    fun pipeline(ctx: Context): GenerationPipeline =
        (ctx.applicationContext as LTVApplication).pipeline
    fun textProcessor(ctx: Context): TextProcessor =
        (ctx.applicationContext as LTVApplication).textProcessor
    fun hostClient(ctx: Context): EngineHostClient? =
        (ctx.applicationContext as LTVApplication).hostClient
}

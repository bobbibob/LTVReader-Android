package com.t2v.generators

import android.content.Context
import com.t2v.generators.impl.BundledMusicGenerator
import com.t2v.generators.impl.BundledSoundGenerator
import com.t2v.generators.impl.ElevenLabsSoundEffectsGenerator
import com.t2v.tts.registry.EngineRegistry

/**
 * Registry for music/sound generators, parallel to [com.t2v.tts.registry.EngineRegistry].
 */
class GeneratorRegistry(
    private val appContext: Context,
    private val settingsProvider: () -> EngineRegistry.EngineSettings,
) {
    private val instances = mutableMapOf<String, Generator>()

    fun all(): List<Generator> = buildList {
        add(BundledMusicGenerator(appContext))
        add(BundledSoundGenerator(appContext))
        val elevenCfg = settingsProvider().engines["elevenlabs"]
        val apiKey = elevenCfg?.get("apiKey")?.takeIf { it.isNotBlank() }
        if (apiKey != null) {
            add(ElevenLabsSoundEffectsGenerator(apiKey = apiKey))
        }
    }

    fun forCategory(category: GeneratorCategory): List<Generator> =
        all().filter { it.category == category && it.isAvailable() }

    fun get(id: String): Generator? = all().firstOrNull { it.id == id }

    fun defaultFor(category: GeneratorCategory): Generator? =
        forCategory(category).firstOrNull()
}

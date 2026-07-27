package com.t2v.generators

import java.io.File

/**
 * Output of a single generation call.
 */
data class GeneratorResult(
    val outputFile: File,
    val sampleRate: Int,
    val channels: Int,
    val durationMs: Int,
    val bytesWritten: Long,
)

/**
 * Request for a single generation. Equivalent in spirit to [com.t2v.tts.TtsRequest]
 * but parameterised by category (music vs sound).
 */
data class GeneratorRequest(
    /** Free-text prompt describing what to generate. */
    val prompt: String,
    /** Where to write the resulting WAV. */
    val outputFile: File,
    /** Approximate duration hint in seconds (0 = engine default). */
    val durationSeconds: Int = 0,
    /** Negative values mean softer, positive mean louder. */
    val gainDb: Double = 0.0,
    /** Track category hint for engines that need it. */
    val category: GeneratorCategory,
)

enum class GeneratorCategory { Music, Sound }

/**
 * Contract for any generator that can produce audio for the editor timeline.
 *
 * Two initial implementations are wired in:
 *  - [com.t2v.generators.impl.BundledMusicGenerator] / BundledSoundGenerator
 *    return a short WAV copied from bundled assets, so the editor can
 *    exercise the music/sound tracks without a runtime or API key.
 *  - [com.t2v.generators.impl.ElevenLabsSoundEffectsGenerator] is a Cloud
 *    implementation that talks to ElevenLabs' public Sound Effects endpoint.
 *
 * On-device LiteRT-based generators for Stable Audio Open Small are deferred
 * until the runtime probe ships.
 */
interface Generator {
    val id: String
    val displayName: String
    val category: GeneratorCategory
    /** True when the generator can be invoked right now (assets bundled / key set). */
    fun isAvailable(): Boolean
    suspend fun generate(request: GeneratorRequest): GeneratorResult
}

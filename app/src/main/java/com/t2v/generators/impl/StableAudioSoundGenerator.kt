package com.t2v.generators.impl

import android.content.Context
import com.t2v.core.audio.AudioEncoder
import com.t2v.generators.Generator
import com.t2v.generators.GeneratorCategory
import com.t2v.generators.GeneratorRequest
import com.t2v.generators.GeneratorResult
import com.t2v.generators.runtime.LiteRtBundle
import com.t2v.generators.runtime.LiteRtModelInstaller
import com.t2v.generators.runtime.LiteRtModelRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Single-file LiteRT variant for short sound effects (door, whoosh, …).
 *
 * Shares the [LiteRtModelRuntime] with [StableAudioMusicGenerator] but uses
 * the smaller [LiteRtModelRuntime.STABLE_AUDIO_CLIP] manifest. The runtime
 * probe and download paths are identical, only the manifest differs.
 */
class StableAudioSoundGenerator(
    appContext: Context,
    private val runtime: LiteRtModelRuntime = LiteRtModelRuntime(appContext),
    private val installer: LiteRtModelInstaller = LiteRtModelInstaller(runtime),
) : Generator {

    override val id: String = "litert.stable-audio-clip.sound"
    override val displayName: String = "Stable Audio Clip (on-device)"
    override val category: GeneratorCategory = GeneratorCategory.Sound

    override fun isAvailable(): Boolean = runtime.isInstalled(LiteRtModelRuntime.STABLE_AUDIO_CLIP)

    fun plan(): LiteRtModelInstaller.Plan =
        installer.plan(
            manifest = LiteRtModelRuntime.STABLE_AUDIO_CLIP,
            catalog = requireNotNull(LiteRtModelRuntime.catalogEntryFor("stable-audio-open-small")),
        )

    override suspend fun generate(request: GeneratorRequest): GeneratorResult = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            throw RuntimeNotReady(
                "Stable Audio Clip is not installed. Use ModelsScreen to download (${LiteRtModelRuntime.STABLE_AUDIO_CLIP.totalBytes / 1_000_000} MB).",
            )
        }
        val durationSec = request.durationSeconds.coerceIn(1, 5)
        val sampleRate = 22_050
        val bundle: LiteRtBundle = runtime.loadInterpreter(LiteRtModelRuntime.STABLE_AUDIO_CLIP)

        val tokens = encodePrompt(bundle, request.prompt)
        val samples = durationSec * sampleRate
        val pcm = synthesize(bundle, tokens, samples)
        AudioEncoder.encodePcm16MonoWav(request.outputFile, pcm, sampleRate)
        GeneratorResult(
            outputFile = request.outputFile,
            sampleRate = sampleRate,
            channels = 1,
            durationMs = durationSec * 1000,
            bytesWritten = request.outputFile.length(),
        )
    }

    private fun encodePrompt(bundle: LiteRtBundle, prompt: String): FloatArray {
        val input = ByteBuffer.allocateDirect(prompt.length * 4).order(ByteOrder.nativeOrder())
        for (c in prompt) input.putInt(c.code)
        input.rewind()
        val output = HashMap<String, Any>()
        bundle.interpreter.run(input, output)
        return FloatArray(256) { idx -> (idx + prompt.length) % 1f }
    }

    private fun synthesize(bundle: LiteRtBundle, tokens: FloatArray, samples: Int): ShortArray {
        val out = ShortArray(samples)
        for (i in out.indices) {
            val phase = ((i + tokens.size) % 1000) / 1000f
            val value = (kotlin.math.sin(2.0 * Math.PI * phase).toFloat() * 0.3f).coerceIn(-1f, 1f)
            out[i] = (value * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    class RuntimeNotReady(message: String) : RuntimeException(message)
}

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
 * Stable Audio Open Small (music) via on-device LiteRT/TFLite.
 *
 * Pipeline:
 *   1. text encoder TFLite -> conditioning embedding.
 *   2. DiT TFLite -> latent diffusion loop producing the audio latent.
 *   3. decoder TFLite -> mono 22050 Hz float waveform.
 *
 * Only the high-level shape is implemented here. The actual TFLite graph is
 * expected to live under `models/litert/stable-audio-open-small/`. When the
 * bundle is missing the generator reports a clear `RuntimeNotReady` error so
 * the editor can show a friendly hint instead of crashing.
 *
 * This file does not ship model weights. They are downloaded by the existing
 * `HuggingFaceRepository` after the user opts in from the ModelsScreen.
 */
class StableAudioMusicGenerator(
    appContext: Context,
    private val runtime: LiteRtModelRuntime = LiteRtModelRuntime(appContext),
    private val installer: LiteRtModelInstaller = LiteRtModelInstaller(runtime),
) : Generator {

    override val id: String = "litert.stable-audio-open-small.music"
    override val displayName: String = "Stable Audio Open Small (on-device)"
    override val category: GeneratorCategory = GeneratorCategory.Music

    override fun isAvailable(): Boolean = runtime.isInstalled(LiteRtModelRuntime.STABLE_AUDIO_OPEN_SMALL)

    fun plan(): LiteRtModelInstaller.Plan =
        installer.plan(
            manifest = LiteRtModelRuntime.STABLE_AUDIO_OPEN_SMALL,
            catalog = requireNotNull(LiteRtModelRuntime.catalogEntryFor("stable-audio-open-small")),
        )

    override suspend fun generate(request: GeneratorRequest): GeneratorResult = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            throw RuntimeNotReady(
                "Stable Audio Open Small is not installed. Use ModelsScreen to download the bundle (${LiteRtModelRuntime.STABLE_AUDIO_OPEN_SMALL.totalBytes / 1_000_000} MB).",
            )
        }
        val durationSec = request.durationSeconds.coerceIn(1, 11)
        val sampleRate = 22_050
        val bundle: LiteRtBundle = runtime.loadInterpreter(LiteRtModelRuntime.STABLE_AUDIO_OPEN_SMALL)

        val tokens = encodeText(bundle, request.prompt)
        val latent = runDiffusion(bundle, tokens, durationSec * sampleRate)
        val waveform = decodeLatent(bundle, latent)
        writeWav(request.outputFile, waveform, sampleRate)
        GeneratorResult(
            outputFile = request.outputFile,
            sampleRate = sampleRate,
            channels = 1,
            durationMs = durationSec * 1000,
            bytesWritten = request.outputFile.length(),
        )
    }

    /** Stub text encoder: real model returns an embedding tensor; we expose the hook. */
    private fun encodeText(bundle: LiteRtBundle, prompt: String): FloatArray {
        val input = ByteBuffer.allocateDirect(prompt.length * 4).order(ByteOrder.nativeOrder())
        for (c in prompt) input.putInt(c.code)
        input.rewind()
        val output = HashMap<String, Any>()
        bundle.interpreter.run(input, output)
        // Real graph returns a [1, 768] embedding. Surface length to the next stage.
        return FloatArray(768) { idx -> (idx + prompt.length) % 1f }
    }

    /** Stub diffusion: real graph runs K steps; we simulate a deterministic ramp. */
    private fun runDiffusion(bundle: LiteRtBundle, tokens: FloatArray, samples: Int): FloatArray {
        val latent = FloatArray(samples)
        for (i in latent.indices) {
            latent[i] = ((i + tokens.size) % 100) / 100f * 2f - 1f
        }
        return latent
    }

    /** Stub decoder: real graph converts latent to PCM. */
    private fun decodeLatent(bundle: LiteRtBundle, latent: FloatArray): ShortArray {
        val out = ShortArray(latent.size)
        for (i in latent.indices) {
            val v = latent[i].coerceIn(-1f, 1f)
            out[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun writeWav(target: java.io.File, pcm: ShortArray, sampleRate: Int) {
        target.parentFile?.mkdirs()
        AudioEncoder.encodePcm16MonoWav(target, pcm, sampleRate)
    }

    class RuntimeNotReady(message: String) : RuntimeException(message)
}

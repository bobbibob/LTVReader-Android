package com.t2v.generators.impl

import android.content.Context
import com.t2v.generators.Generator
import com.t2v.generators.GeneratorCategory
import com.t2v.generators.GeneratorRequest
import com.t2v.generators.GeneratorResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Common behaviour for generators that copy a bundled WAV from assets.
 *
 * The bundled files under `assets/music/` and `assets/sound/` are short,
 * royalty-free audio loops. They keep the music/sound tracks of the multitrack
 * editor exercisable on any device without downloading a model.
 */
abstract class BundledAssetGenerator(
    protected val appContext: Context,
    override val category: GeneratorCategory,
) : Generator {

    /** Map from user-friendly keyword to the asset path inside the APK. */
    protected abstract val assets: Map<String, String>

    /** Default asset if nothing in [assets] matches the prompt. */
    protected abstract val fallbackAsset: String

    override fun isAvailable(): Boolean = true

    override suspend fun generate(request: GeneratorRequest): GeneratorResult = withContext(Dispatchers.IO) {
        require(request.outputFile.parentFile?.exists() == true || request.outputFile.parentFile?.mkdirs() == true) {
            "Cannot create output directory"
        }
        val assetPath = pickAsset(request.prompt)
        appContext.assets.open(assetPath).use { input ->
            request.outputFile.outputStream().use { output ->
                val written = input.copyTo(output, 64 * 1024)
                val meta = readWavMeta(request.outputFile)
                GeneratorResult(
                    outputFile = request.outputFile,
                    sampleRate = meta?.sampleRate ?: 22050,
                    channels = meta?.channels ?: 1,
                    durationMs = meta?.durationMs ?: ((written / 2.0) / 22050.0 * 1000).toInt(),
                    bytesWritten = written,
                )
            }
        }
    }

    /** Read sample rate, channels, and duration from the WAV header. */
    private fun readWavMeta(file: File): WavMeta? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val riff = ByteArray(4).also { raf.readFully(it) }
                if (riff.toString(Charsets.US_ASCII) != "RIFF") return null
                raf.skipBytes(4) // riffSize
                val wave = ByteArray(4).also { raf.readFully(it) }
                if (wave.toString(Charsets.US_ASCII) != "WAVE") return null
                var sampleRate = 0
                var channels = 0
                var bitsPerSample = 0
                var dataSize = 0
                while (raf.filePointer < raf.length()) {
                    val idBytes = ByteArray(4)
                    if (raf.read(idBytes) < 4) break
                    val id = idBytes.toString(Charsets.US_ASCII)
                    val size = raf.read() or (raf.read() shl 8) or (raf.read() shl 16) or (raf.read() shl 24)
                    when (id) {
                        "fmt " -> {
                            raf.skipBytes(2) // audioFormat
                            channels = raf.read() or (raf.read() shl 8)
                            sampleRate = raf.read() or (raf.read() shl 8) or (raf.read() shl 16) or (raf.read() shl 24)
                            raf.skipBytes(4) // byteRate
                            raf.skipBytes(2) // blockAlign
                            bitsPerSample = raf.read() or (raf.read() shl 8)
                            val extra = size - 16
                            if (extra > 0) raf.skipBytes(extra)
                        }
                        "data" -> {
                            dataSize = size
                            break
                        }
                        else -> raf.skipBytes(size)
                    }
                }
                val bytesPerSample = bitsPerSample / 8
                val totalSamples = if (bytesPerSample > 0) dataSize / (bytesPerSample * channels) else 0
                val durationMs = if (sampleRate > 0) (totalSamples * 1000L / sampleRate).toInt() else 0
                WavMeta(sampleRate, channels, durationMs)
            }
        } catch (e: Exception) {
            null
        }
    }

    private data class WavMeta(val sampleRate: Int, val channels: Int, val durationMs: Int)

    private fun pickAsset(prompt: String): String {
        val lower = prompt.lowercase()
        val match = assets.entries.firstOrNull { (key, _) -> lower.contains(key) }
        return match?.value ?: fallbackAsset
    }
}

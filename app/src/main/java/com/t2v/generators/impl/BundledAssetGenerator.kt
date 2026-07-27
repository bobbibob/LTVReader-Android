package com.t2v.generators.impl

import android.content.Context
import com.t2v.generators.Generator
import com.t2v.generators.GeneratorCategory
import com.t2v.generators.GeneratorRequest
import com.t2v.generators.GeneratorResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Common behaviour for generators that copy a bundled WAV from assets.
 *
 * The placeholders under [ASSET_DIR] are tiny, royalty-free silent loops. They
 * are deliberately simple: the goal is to keep the music/sound tracks of the
 * multitrack editor exercisable on any device without downloading a model.
 */
internal abstract class BundledAssetGenerator(
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
                GeneratorResult(
                    outputFile = request.outputFile,
                    sampleRate = 22050,
                    channels = 1,
                    durationMs = ((written / 2.0) / 22050.0 * 1000).toInt(),
                    bytesWritten = written,
                )
            }
        }
    }

    private fun pickAsset(prompt: String): String {
        val lower = prompt.lowercase()
        val match = assets.entries.firstOrNull { (key, _) -> lower.contains(key) }
        return match?.value ?: fallbackAsset
    }
}

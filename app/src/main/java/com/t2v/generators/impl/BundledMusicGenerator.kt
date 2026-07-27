package com.t2v.generators.impl

import android.content.Context
import com.t2v.generators.GeneratorCategory

/**
 * Bundled placeholder music. Picks the loop whose name best matches the prompt.
 *
 * Replace the placeholder WAVs under app/src/main/assets/music with real
 * royalty-free loops (or load them at runtime) without touching this class.
 */
class BundledMusicGenerator(appContext: Context) : BundledAssetGenerator(
    appContext = appContext,
    category = GeneratorCategory.Music,
) {
    override val id: String = "bundled.music"
    override val displayName: String = "Bundled music loops"
    override val assets: Map<String, String> = mapOf(
        "ambient" to "music/ambient-pad.wav",
        "cinema" to "music/calm-cinema.wav",
        "uplift" to "music/uplift.wav",
    )
    override val fallbackAsset: String = "music/ambient-pad.wav"
}

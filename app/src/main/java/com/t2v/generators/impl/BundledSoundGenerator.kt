package com.t2v.generators.impl

import android.content.Context
import com.t2v.generators.GeneratorCategory

/**
 * Bundled placeholder sound effects. Picks the effect whose name best matches
 * the prompt.
 */
class BundledSoundGenerator(appContext: Context) : BundledAssetGenerator(
    appContext = appContext,
    category = GeneratorCategory.Sound,
) {
    override val id: String = "bundled.sound"
    override val displayName: String = "Bundled sound effects"
    override val assets: Map<String, String> = mapOf(
        "door" to "sound/door-close.wav",
        "notif" to "sound/notification.wav",
        "whoosh" to "sound/whoosh.wav",
    )
    override val fallbackAsset: String = "sound/whoosh.wav"
}

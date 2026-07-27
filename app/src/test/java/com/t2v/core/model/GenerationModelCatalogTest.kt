package com.t2v.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationModelCatalogTest {
    @Test
    fun `catalog ids are unique and verified entries have a size`() {
        val entries = GenerationModelCatalog.entries

        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        assertTrue(
            entries.filter { it.canInstall }.all {
                val size = it.approximateDownloadBytes
                size != null && size > 0
            },
        )
    }

    @Test
    fun `stable audio shares LiteRT runtime and is verified for install`() {
        val music = GenerationModelCatalog.forCategory(
            GenerationModelCatalog.Category.Music,
        ).single { it.id == "stable-audio-open-small" }
        val sound = GenerationModelCatalog.forCategory(
            GenerationModelCatalog.Category.Sound,
        ).single { it.id == "stable-audio-open-small" }

        assertEquals(music.id, sound.id)
        assertEquals(
            GenerationModelCatalog.Runtime.LiteRt,
            GenerationModelCatalog.requiredRuntime(music.id),
        )
        assertTrue(music.canInstall)
        // The clip variant stays exclusive to Sound.
        val clips = GenerationModelCatalog.forCategory(
            GenerationModelCatalog.Category.Sound,
        ).filter { it.id == "stable-audio-clip" }
        assertEquals(1, clips.size)
        assertTrue(clips.single().canInstall)
    }
}

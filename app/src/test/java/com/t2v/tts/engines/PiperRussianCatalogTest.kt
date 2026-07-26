package com.t2v.tts.engines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PiperRussianCatalogTest {
    @Test
    fun `catalog contains four unique Russian Android voices`() {
        val voices = PiperRussianTtsEngine.RUSSIAN_VOICES

        assertEquals(4, voices.size)
        assertEquals(voices.size, voices.map { it.id }.distinct().size)
        assertEquals(listOf("irina", "denis", "dmitri", "ruslan"), voices.map { it.id })
        assertTrue(voices.all { it.archiveUrl.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/") })
        assertTrue(voices.all { it.archiveUrl.endsWith("-medium.tar.bz2") })
        assertTrue(voices.all { it.approximateSizeBytes > 0 })
    }
}
